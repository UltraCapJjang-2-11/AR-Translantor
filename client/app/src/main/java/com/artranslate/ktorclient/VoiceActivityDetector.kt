package com.example.text

class VoiceActivityDetector {
    private var inSpeech = false
    private var silenceCounter = 0
    private val SILENCE_THRESHOLD = 500.0  // RMS 임계값 예시
    private val SILENCE_DURATION = 10      // 연속 측정에서 이 값 이상 연속 무음 시 발화 종료

    fun processBuffer(buffer: ShortArray): Boolean {
        val rms = calculateRMS(buffer)
        return if (rms > SILENCE_THRESHOLD) {
            // 음성이 계속 감지 중
            inSpeech = true
            silenceCounter = 0
            false
        } else {
            // 무음
            if (inSpeech) {
                silenceCounter++
                if (silenceCounter > SILENCE_DURATION) {
                    // 발화 종료로 판단
                    inSpeech = false
                    silenceCounter = 0
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private fun calculateRMS(buffer: ShortArray): Double {
        var sum = 0.0
        for (sample in buffer) {
            sum += (sample * sample).toDouble()
        }
        return Math.sqrt(sum / buffer.size)
    }
}
