package com.example.myapplication2

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private val capturedImageBitmap = mutableStateOf<Bitmap?>(null)
    private val translatedImageBitmap = mutableStateOf<Bitmap?>(null)
    private val extractedText = mutableStateOf("")
    private val translatedText = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted) {
                    when (permissionName) {
                        Manifest.permission.CAMERA -> {
                            openCamera()
                        }
                    }
                } else {
                    Toast.makeText(this, "$permissionName 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        takePictureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                imageBitmap?.let {
                    capturedImageBitmap.value = it
                    extractAndTranslateText(it)
                }
            }
        }

        setContent {
            val bitmap = translatedImageBitmap.value ?: capturedImageBitmap.value
            CameraTranslationScreen(
                capturedImage = bitmap,
                extractedText = extractedText.value,
                translatedText = translatedText.value,
                onOpenCamera = { checkCameraPermissionAndOpen() }
            )
        }
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PERMISSION_GRANTED) {
            openCamera()
        } else {
            // 항상 권한을 요청하도록 변경
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(cameraIntent)
    }

    private fun extractAndTranslateText(image: Bitmap) {
        val inputImage = InputImage.fromBitmap(image, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                extractedText.value = visionText.text
                val translatedTexts = mutableListOf<Pair<String, android.graphics.Rect>>()
                val client = OkHttpClient()

                visionText.textBlocks.forEach { block ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val translated = translateText(block.text, client)
                        if (translated != null) {
                            withContext(Dispatchers.Main) {
                                translatedTexts.add(Pair(translated, block.boundingBox!!))
                                translatedText.value += "$translated\n"
                                if (translatedTexts.size == visionText.textBlocks.size) {
                                    overlayTranslatedText(image, translatedTexts, block)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "번역 실패: 네트워크 문제나 API 키 문제일 수 있습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "텍스트 추출 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun translateText(text: String, client: OkHttpClient): String? {
        val apiKey = "AIzaSyDhag1OMnOMbkl2jEoepgCsWLGZdM0seeg"
        val url = "https://translation.googleapis.com/language/translate/v2?key=$apiKey&q=$text&target=ko"

        val request = Request.Builder().url(url).build()
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                json.getJSONObject("data").getJSONArray("translations").getJSONObject(0).getString("translatedText")
            } else {
                Log.e("Translation Error", "Response not successful: ${response.code}, message: ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e("Translation Error", "Exception occurred: ${e.message}")
            null
        }
    }

    private fun overlayTranslatedText(image: Bitmap, translatedTexts: List<Pair<String, android.graphics.Rect>>, block: com.google.mlkit.vision.text.Text.TextBlock) {
        val mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.RED
            textSize = calculateFontSize(block.boundingBox?.height() ?: 48) // 텍스트 블록 크기에 맞춰 텍스트 크기 조정
        }

        translatedTexts.forEach { (text, rect) ->
            paint.textSize = calculateFontSize(rect.height()) // 각각의 텍스트 크기에 맞춰 텍스트 크기 조정
            canvas.drawText(text, rect.left.toFloat(), rect.bottom.toFloat(), paint)
        }

        translatedImageBitmap.value = mutableBitmap
    }

    private fun calculateFontSize(rectHeight: Int): Float {
        // 텍스트 블록의 높이를 기반으로 텍스트 크기 계산
        return rectHeight * 0.8f
    }
}

@Composable
fun CameraTranslationScreen(
    capturedImage: Bitmap?,
    extractedText: String,
    translatedText: String,
    onOpenCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { onOpenCamera() }) {
            Text(text = "카메라 열기")
        }

        Spacer(modifier = Modifier.height(16.dp))

        capturedImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "추출된 텍스트:")
        Text(text = extractedText)

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "번역된 텍스트:")
        Text(text = translatedText)
    }
}

// Add the following dependency to your build.gradle file:
// implementation 'com.squareup.okhttp3:okhttp:4.9.3'
