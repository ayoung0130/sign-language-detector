package com.ayeong.sign_language_detector.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var maxNumHands: Int = DEFAULT_NUM_HANDS,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    val handLandmarkerHelperListener: LandmarkerListener? = null
) {

    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    // 현재 설정을 사용하여 Hand landmarker를 초기화
    // CPU는 메인 스레드에서 생성되고 백그라운드 스레드에서 사용되는 Landmarker와 함께 사용할 수 있지만,
    // GPU 대리자는 Landmarker를 초기화한 스레드에서 사용해야 함
    private fun setupHandLandmarker() {
        // 일반적인 hand landmarker 옵션 설정
        val baseOptionBuilder = BaseOptions.builder()

        // 모델 실행에 지정된 하드웨어를 사용. 기본값은 CPU.
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }

            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_HAND_LANDMARKER_TASK)

        // runningMode가 handLandmarkerHelperListener와 일치하는지 확인.
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (handLandmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "runningMode가 LIVE_STREAM일 때 handLandmarkerHelperListener가 설정되어야 합니다."
                    )
                }
            }

            else -> { }
        }

        try {
            val baseOptions = baseOptionBuilder.build()
            // base 옵션과 Hand Landmarker에만 사용되는 특정 옵션으로 옵션 빌더를 생성.
            val optionsBuilder =
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinTrackingConfidence(minHandTrackingConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setNumHands(maxNumHands)
                    .setRunningMode(runningMode)

            // ResultListener와 ErrorListener는 LIVE_STREAM 모드에서만 사용.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
            }

            val options = optionsBuilder.build()
            handLandmarker =
                HandLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            Log.e(
                TAG, "MediaPipe가 오류로 인해 태스크를 로드하지 못했습니다: " + e.message
            )
        } catch (e: RuntimeException) {
            Log.e(
                TAG,
                "이미지 분류기가 모델 로드에 실패했습니다: " + e.message
            )
        }
    }

    // ImageProxy를 MP Image로 변환하고 HandlandmarkerHelper에 전달
    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "RunningMode.LIVE_STREAM이 아닌 상태에서 detectLiveStream을 호출하려고 합니다."
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // 프레임에서 RGB 비트를 복사하여 비트맵 버퍼에 저장
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // 카메라에서 받은 프레임을 표시되는 방향과 동일하게 회전
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // 사용자가 전면 카메라를 사용할 경우 이미지 반전
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // 입력 Bitmap 객체를 MPImage 객체로 변환하여 추론 실행
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    // MediaPipe Hand Landmarker API를 사용하여 손 랜드마크 실행
    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        handLandmarker?.detectAsync(mpImage, frameTime)
        // RunningMode.LIVE_STREAM을 사용하므로, 랜드마크 결과는 returnLivestreamResult 함수에서 반환
    }

    // 이 HandLandmarkerHelper의 호출자에게 랜드마크 결과 반환
    private fun returnLivestreamResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        handLandmarkerHelperListener?.onHandResults(
            ResultBundle(
                listOf(result),
                input.height,
                input.width
            )
        )
    }

    data class ResultBundle(
        val results: List<HandLandmarkerResult>,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onHandResults(resultBundle: ResultBundle)
    }

    companion object {
        const val TAG = "HandLandmarkerHelper"
        private const val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_HANDS = 2
    }
}
