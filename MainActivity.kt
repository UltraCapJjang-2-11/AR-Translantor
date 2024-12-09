

/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.artranslate.ktorclient
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.artranslate.ktorclient.helpers.CameraPermissionHelper
import com.artranslate.ktorclient.helpers.FullScreenHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.widget.TextView
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ux.ArFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Button
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation




data class TranslatedText(val original: String, val translated: String)

class TextAdapter(private val textList: MutableList<TranslatedText>) :
    RecyclerView.Adapter<TextAdapter.TextViewHolder>() {

    class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val originalTextView: TextView = itemView.findViewById(R.id.originalTextView)
        val translatedTextView: TextView = itemView.findViewById(R.id.translatedTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_text_with_translation, parent, false)
        return TextViewHolder(view)
    }

    override fun onBindViewHolder(holder: TextViewHolder, position: Int) {
        val item = textList[position]
        holder.originalTextView.text = item.original
        holder.translatedTextView.text = item.translated
    }

    override fun getItemCount(): Int = textList.size

    fun addText(newItem: TranslatedText) {
        textList.add(newItem)
        notifyItemInserted(textList.size - 1)
    }

    fun clearTexts() {
        textList.clear()
        notifyDataSetChanged()
    }
}


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    //// 추가
    private lateinit var arFragment: ArFragment
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var recyclerView: RecyclerView
    private lateinit var textAdapter: TextAdapter

    private val translator by lazy {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH) // 소스 언어: 영어
            .setTargetLanguage(TranslateLanguage.KOREAN)  // 목표 언어: 한국어
            .build()
        Translation.getClient(options)
    }

    private var lastFrameTime: Long = 0 // 마지막 프레임 업데이트 시간
    private val frameInterval = 1000L  // 1초 (밀리초 단위)


    // 중복 제거를 위한 Set
    private val uniqueTexts = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // RecyclerView 설정
        recyclerView = findViewById(R.id.textRecyclerView)
        textAdapter = TextAdapter(mutableListOf())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = textAdapter


        // 번역 모델 다운로드
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                Toast.makeText(this, "번역 모델이 준비되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "번역 모델 다운로드 실패: ${e.localizedMessage}")
            }

        // 초기화 버튼 설정
        val resetButton: Button = findViewById(R.id.resetButton)
        resetButton.setOnClickListener {
            uniqueTexts.clear() // Set 초기화
            textAdapter.clearTexts() // RecyclerView 초기화
            Toast.makeText(this, "텍스트 목록이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
        }

        // ArFragment 초기화
        arFragment = ArFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.arFragmentContainer, arFragment)
            .commit()

        // ARCore 초기화 및 프레임 업데이트 리스너 추가
        arFragment.viewLifecycleOwnerLiveData.observe(this) { viewLifecycleOwner ->
            if (viewLifecycleOwner != null) {
                arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
                    val currentTime = System.currentTimeMillis()

                    // 1초 간격으로만 프레임 업데이트 실행
                    if (currentTime - lastFrameTime >= frameInterval) {
                        val frame = arFragment.arSceneView.arFrame
                        if (frame != null && frame.camera.trackingState == TrackingState.TRACKING) {
                            processARFrameForTextRecognition(frame)
                        }
                        lastFrameTime = currentTime
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun configureSession(session: Session) {

        try {
            val cameraConfigList = session.getSupportedCameraConfigs()
            if (cameraConfigList.isNotEmpty()) {
                session.cameraConfig = cameraConfigList[0] // 첫 번째(가장 높은 해상도) 설정 사용
            }

            session.configure(
                session.config.apply {
                    focusMode = Config.FocusMode.FIXED
                    lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                }
            )
        } catch (e: Exception) {
            Log.e("ARCore", "카메라 설정 실패: ${e.localizedMessage}")
        }
    }

    private fun processARFrameForTextRecognition(frame: Frame) {
        var image: android.media.Image? = null
        try {
            image = frame.acquireCameraImage()
            val inputImage = InputImage.fromMediaImage(image, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    for (block in visionText.textBlocks) {
                        val originalText = block.text

                        // 번역 작업 추가
                        translator.translate(originalText)
                            .addOnSuccessListener { translatedText ->
                                if (uniqueTexts.add(originalText)) { // 원문 기준으로 중복 검사
                                    val item = TranslatedText(original = originalText, translated = translatedText)
                                    textAdapter.addText(item) // RecyclerView에 추가
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "번역 실패: ${e.localizedMessage}")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "텍스트 인식 실패: ${e.localizedMessage}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "이미지 획득 실패: ${e.localizedMessage}")
        } finally {
            image?.close()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }


}