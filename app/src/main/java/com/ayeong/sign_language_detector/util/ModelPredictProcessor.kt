package com.ayeong.sign_language_detector.util

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelPredictProcessor(context: Context) {

    private val tflite: Interpreter

    init {
        val model = loadModelFile(context, "sign_language_detect_model.tflite")
        tflite = Interpreter(model)
    }

    private fun loadModelFile(context: Context, modelFileName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = fileDescriptor.createInputStream()
        val byteBuffer = ByteBuffer.allocateDirect(fileDescriptor.declaredLength.toInt())
        byteBuffer.order(ByteOrder.nativeOrder())
        inputStream.channel.read(byteBuffer)
        inputStream.close()
        byteBuffer.position(0)
        return byteBuffer
    }

    fun predict(data: List<FloatArray>): MutableList<String> {
        // 전체 시퀀스를 생성
        val inputSequences = createSequences(data, 15, 5)

        // TFLite 모델에 입력할 배열 준비
        val inputArray = Array(inputSequences.size) { index ->
            inputSequences[index].toTypedArray()
        }
        val outputArray = Array(inputSequences.size) { FloatArray(actions.size) }

        // 모델에 전체 시퀀스를 전달하여 예측 수행
        tflite.run(inputArray, outputArray)

        // outputArray의 내용을 로그로 출력
        outputArray.forEachIndexed { index, output ->
            Log.d("ModelPredictProcessor", "outputArray[$index]: ${output.contentToString()}")
        }

        // 예측 결과 처리
        val predictions = mutableListOf<Int>()
        for (output in outputArray) {
            predictions.add(output.indexOfMax())
        }
        Log.d("ModelPredictProcessor", "predictions: $predictions")

        // 예측된 인덱스를 해당하는 단어로 변환하여 반환
        return predictions.map { index -> actions[index] }.toMutableList()
    }

    private fun createSequences(
        data: List<FloatArray>,
        sequenceLength: Int,
        jumpingWindow: Int
    ): List<List<FloatArray>> {
        Log.d("ModelPredictProcessor", "Data size: ${data.size}")

        val sequences = mutableListOf<List<FloatArray>>()
        for (i in 0..data.size - sequenceLength step jumpingWindow) {
            val sequence = data.subList(i, i + sequenceLength)
            Log.d("ModelPredictProcessor", "window: $i, ${i + sequenceLength}")
            sequences.add(sequence)

            // 배열의 내용을 출력하도록 수정
            val sequenceContent = sequence.joinToString(", ") { it.contentToString() }
            Log.d("ModelPredictProcessor", "Sequence: $sequenceContent")
        }

        Log.d("ModelPredictProcessor", "Sequence data size: ${sequences.size}")

        return sequences
    }

    private fun FloatArray.indexOfMax(): Int {
        var maxIndex = 0
        for (i in indices) {
            if (this[i] > this[maxIndex]) maxIndex = i
        }
        return maxIndex
    }

    companion object {
        val actions = arrayOf("가렵다", "기절", "부러지다", "어제", "어지러움", "열나다", "오늘", "진통제", "창백하다", "토하다")
    }
}