// TextRecognitionAnalyzer.kt

package com.example.text

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@OptIn(ExperimentalGetImage::class)
class TextRecognitionAnalyzer(
    private val context: Context,
    private val graphicOverlay: GraphicOverlay
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotation)

            recognizer.process(image)
                .addOnSuccessListener { texts ->
                    // 이미지 원본 크기 설정
                    graphicOverlay.setImageSourceInfo(image.width, image.height, false)

                    // 이전 그래픽 클리어
                    graphicOverlay.clear()

                    // 인식된 텍스트를 표시할 그래픽 추가
                    graphicOverlay.add(TextGraphic(graphicOverlay, texts))
                }
                .addOnFailureListener { e ->
                    Log.e("TextRecognitionAnalyzer", "Text recognition failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
