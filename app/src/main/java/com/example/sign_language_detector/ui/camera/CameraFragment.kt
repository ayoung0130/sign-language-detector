package com.example.sign_language_detector.ui.camera

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.example.sign_language_detector.R
import com.example.sign_language_detector.databinding.FragmentCameraBinding
import com.example.sign_language_detector.repository.HandLandmarkerHelper
import com.example.sign_language_detector.repository.PoseLandmarkerHelper
import com.example.sign_language_detector.ui.splash.SplashFragment
import com.example.sign_language_detector.usecase.CameraUseCase
import com.example.sign_language_detector.usecase.DetectUseCase
import com.example.sign_language_detector.util.LandmarkProcessor
import com.example.sign_language_detector.util.ModelPredictProcessor
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), HandLandmarkerHelper.LandmarkerListener,
    PoseLandmarkerHelper.LandmarkerListener {

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var viewModel: CameraViewModel
    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private var imageHandAnalyzer: ImageAnalysis? = null
    private var imagePoseAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** 차단된 ML 작업은 이 실행기를 사용하여 수행 */
    private lateinit var backgroundExecutor: ExecutorService

    // 손 감지 상태를 추적하기 위한 변수
    private var isHandDetected = false
    private var latestLandmarkData: List<List<Float>> = emptyList()

    // 포즈 데이터를 임시로 저장할 변수
    private var latestPoseResultBundle: PoseLandmarkerHelper.ResultBundle? = null

    override fun onResume() {
        super.onResume()
        // 모든 권한이 여전히 존재하는지 확인, 사용자가 앱이 일시 중지된 동안 이를 제거할 수 있음
        if (!SplashFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_splash_to_home)
        }

        // 사용자가 전면으로 돌아올 때 HandLandmarkerHelper를 다시 시작
        backgroundExecutor.execute {
            if (handLandmarkerHelper.isClose()) {
                handLandmarkerHelper.setupHandLandmarker()
            }
            if (poseLandmarkerHelper.isClose()) {
                poseLandmarkerHelper.setupPoseLandmarker()
            }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // 백그라운드 실행기 종료
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 백그라운드 실행기 초기화
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // HandLandmarkerHelper와 PoseLandmarkerHelper를 직접 초기화
        handLandmarkerHelper = HandLandmarkerHelper(
            context = requireContext(),
            runningMode = RunningMode.LIVE_STREAM,
            minHandDetectionConfidence = 0.5f,
            minHandTrackingConfidence = 0.5f,
            minHandPresenceConfidence = 0.5f,
            maxNumHands = 2,
            currentDelegate = 0,
            handLandmarkerHelperListener = this@CameraFragment
        )
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = requireContext(),
            runningMode = RunningMode.LIVE_STREAM,
            minPoseDetectionConfidence = 0.5f,
            minPoseTrackingConfidence = 0.5f,
            minPosePresenceConfidence = 0.5f,
            currentDelegate = 0,
            poseLandmarkerHelperListener = this@CameraFragment
        )

        // ViewModel 초기화
        val detectUseCase = DetectUseCase(
            handLandmarkerHelper = handLandmarkerHelper,
            poseLandmarkerHelper = poseLandmarkerHelper,
            executor = backgroundExecutor
        )
        val landmarkProcessor = LandmarkProcessor()
        val modelPredictProcessor = ModelPredictProcessor(requireContext())
        val factory = CameraViewModelFactory(detectUseCase, landmarkProcessor, modelPredictProcessor)
        viewModel = ViewModelProvider(this, factory)[CameraViewModel::class.java]

        fragmentCameraBinding.viewModel = viewModel
        fragmentCameraBinding.lifecycleOwner = viewLifecycleOwner

        Log.d("CameraFragment", "ViewModel initialized: $viewModel")

        // 뷰가 제대로 배치될 때까지 대기
        fragmentCameraBinding.viewFinder.post {
            // 카메라와 그 사용 사례를 설정
            setUpCamera()
        }

    }

    // CameraX 초기화 및 카메라 사용 사례 준비
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // 카메라 사용 사례 빌드 및 바인딩
                val cameraUseCase = CameraUseCase(
                    this,
                    cameraFacing,
                    fragmentCameraBinding.viewFinder,
                    viewModel,
                    backgroundExecutor
                )
                cameraUseCase.bindCameraUseCases(cameraProvider!!)
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageHandAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
        imagePoseAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // 손이 감지된 후 UI 업데이트. 오리지널 이미지 높이/너비를 추출하고 캔버스를 통해 랜드마크를 올바르게 배치
    override fun onHandResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                // 필요한 정보를 OverlayView에 전달하여 캔버스에 그림
                fragmentCameraBinding.overlay.setHandResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // 다시 그리기 강제
                fragmentCameraBinding.overlay.invalidate()

                // 손이 감지되었음을 업데이트
                isHandDetected = true

                Log.d("CameraFragment", "Hand results received. Processing landmarks...")

                // 포즈 결과가 있는 경우 손의 resultBundle과 함께 처리
                latestLandmarkData = viewModel.processLandmarks(
                    resultBundle,
                    latestPoseResultBundle ?: PoseLandmarkerHelper.ResultBundle(emptyList(), 0, 0)
                )
                Log.d("CameraFragment", "Landmark data: $latestLandmarkData")
            }
        }
    }

    // 포즈가 감지된 후 UI 업데이트
    override fun onPoseResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                fragmentCameraBinding.overlay.setPoseResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // 다시 그리기 강제
                fragmentCameraBinding.overlay.invalidate()

                // 포즈 결과를 임시로 저장
                latestPoseResultBundle = resultBundle

                viewModel.processLandmarks(
                    HandLandmarkerHelper.ResultBundle(emptyList(), 0, 0),
                    resultBundle
                )
            }
        }
    }

    override fun onHandError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPoseError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onHandDetectionEmpty() {
        activity?.runOnUiThread {
            if (isHandDetected) {
                Log.d("CameraFragment",
                    "Hand detection empty. Updating predicted word...")
                if (latestLandmarkData.isNotEmpty()) {
                    viewModel.updatePredictedWord(latestLandmarkData)
                } else {
                    Log.d("CameraFragment",
                        "No landmark data available")
                }
                isHandDetected = false
            }
        }
    }

    companion object {
        private const val TAG = "Landmarker"
    }
}
