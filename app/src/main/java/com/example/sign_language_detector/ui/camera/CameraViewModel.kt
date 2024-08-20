package com.example.sign_language_detector.ui.camera

import androidx.camera.core.ImageProxy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.sign_language_detector.usecase.DetectUseCase
import com.example.sign_language_detector.util.LandmarkProcessor
import com.example.sign_language_detector.util.ModelPredictProcessor

class CameraViewModel(
    private val detectUseCase: DetectUseCase,
    private val landmarkProcessor: LandmarkProcessor,
    private val modelPredictProcessor: ModelPredictProcessor
) : ViewModel() {

    private val _predictedWord = MutableLiveData<String>()
    val predictedWord: LiveData<String> get() = _predictedWord

    fun detectHand(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        detectUseCase.detectHand(imageProxy, isFrontCamera)
    }

    fun detectPose(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        detectUseCase.detectPose(imageProxy, isFrontCamera)
    }

    fun calculateHandAngle(handLandmark: Array<FloatArray>): FloatArray {
        return landmarkProcessor.angleHands(handLandmark)
    }

    fun calculatePoseAngle(poseLandmark: Array<FloatArray>): FloatArray {
        return landmarkProcessor.anglePose(poseLandmark)
    }

    fun updatePredictedWord(data: List<FloatArray>) {
        _predictedWord.postValue(modelPredictProcessor.predict(data))    // 예측된 텍스트를 LiveData로 업데이트
    }

}
