// GraphicOverlay.kt

package com.example.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.View
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max

/**
 * 그래픽 오버레이 뷰로, 인식된 텍스트를 화면에 그려줍니다.
 */
class GraphicOverlay(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val lock = ReentrantLock()
    private val graphics = mutableListOf<Graphic>()
    private var imageWidth = 0
    private var imageHeight = 0
    private var isFlipped = false
    private var scaleFactor = 1.0f
    private var postScaleWidthOffset = 0f
    private var postScaleHeightOffset = 0f

    abstract class Graphic(private val overlay: GraphicOverlay) {
        abstract fun draw(canvas: Canvas)

        fun scale(value: Float) = value * overlay.scaleFactor

        fun translateX(x: Float): Float {
            // 반전 상태를 고려하고, postScaleWidthOffset와 scaleFactor 반영
            val scaledX = x * overlay.scaleFactor + overlay.postScaleWidthOffset + 100
            return if (overlay.isFlipped) overlay.width - scaledX else scaledX
        }

        fun translateY(y: Float): Float {
            return y * overlay.scaleFactor + overlay.postScaleHeightOffset - 200
        }
    }

    fun setImageSourceInfo(imageWidth: Int, imageHeight: Int, isFlipped: Boolean) {
        lock.withLock {
            this.imageWidth = imageWidth
            this.imageHeight = imageHeight
            this.isFlipped = isFlipped
        }
        postInvalidate()
    }

    fun clear() {
        lock.withLock {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        lock.withLock {
            graphics.add(graphic)
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        lock.withLock {
            if (imageWidth != 0 && imageHeight != 0) {
                val viewWidth = width.toFloat()
                val viewHeight = height.toFloat()

                // FIT_CENTER 방식으로 스케일 계산
                val widthRatio = viewWidth / imageWidth
                val heightRatio = viewHeight / imageHeight

                scaleFactor = if (widthRatio < heightRatio) {
                    widthRatio
                } else {
                    heightRatio
                }

                postScaleWidthOffset = (viewWidth - imageWidth * scaleFactor) / 2
                postScaleHeightOffset = (viewHeight - imageHeight * scaleFactor) / 2

                // 모든 그래픽 그리기
                for (graphic in graphics) {
                    graphic.draw(canvas)
                }
            }
        }
    }
}
