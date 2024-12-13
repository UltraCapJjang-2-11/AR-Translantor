package com.example.text

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.text.Text
import kotlin.math.max
import kotlin.math.min

/**
 * 인식된 텍스트를 오버레이하는 그래픽 클래스
 */
class TextGraphic(
    overlay: GraphicOverlay,
    private val text: Text
) : GraphicOverlay.Graphic(overlay) {

    private val rectPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4.0f
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 54.0f
    }

    private val labelPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        // 인식된 TextBlock과 Line의 bounding box를 사용
        for (textBlock in text.textBlocks) {
            for (line in textBlock.lines) {
                val lineRect = line.boundingBox ?: continue

                // boundingBox를 화면 좌표계로 변환
                val rect = RectF(line.boundingBox)
                val x0 = translateX(rect.left)  // translateX에서 scaleFactor와 offset 적용
                val x1 = translateX(rect.right)
                rect.left = min(x0, x1)
                rect.right = max(x0, x1)
                rect.top = translateY(rect.top)
                rect.bottom = translateY(rect.bottom)

                // 라인 bounding box 그리기
                canvas.drawRect(rect, rectPaint)

                // 텍스트 표기
                val textValue = line.text
                val textWidth = textPaint.measureText(textValue)
                val textHeight = textPaint.textSize

                // 텍스트 배경 박스
                canvas.drawRect(
                    rect.left - 4.0f,
                    rect.top - textHeight - 8.0f,
                    rect.left + textWidth + 8.0f,
                    rect.top,
                    labelPaint
                )

                // 실제 텍스트 그리기
                canvas.drawText(textValue, rect.left, rect.top - 4.0f, textPaint)
            }
        }
    }
}
