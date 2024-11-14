package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Rect
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var cameraHandler: CameraHandler
    private lateinit var textProcessor: TextProcessor

    private val capturedImageBitmap = mutableStateOf<Bitmap?>(null)
    private val translatedImageBitmap = mutableStateOf<Bitmap?>(null)
    private val extractedText = mutableStateOf("")
    private val translatedText = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionHandler = PermissionHandler(this)
        cameraHandler = CameraHandler(this, ::onImageCaptured)
        textProcessor = TextProcessor(this, ::onTextExtracted, ::onTranslationComplete)

        setContent {
            val bitmap = translatedImageBitmap.value ?: capturedImageBitmap.value
            CameraTranslationScreen(
                capturedImage = bitmap,
                extractedText = extractedText.value,
                translatedText = translatedText.value,
                onOpenCamera = { permissionHandler.checkCameraPermissionAndOpen { cameraHandler.openCamera() } }
            )
        }
    }

    private fun onImageCaptured(image: Bitmap) {
        capturedImageBitmap.value = image
        textProcessor.extractAndTranslateText(image)
    }

    private fun onTextExtracted(text: String) {
        extractedText.value = text
    }

    private fun onTranslationComplete(bitmap: Bitmap, translatedText: String) {
        translatedImageBitmap.value = bitmap
        this.translatedText.value = translatedText
    }
}

class PermissionHandler(private val activity: ComponentActivity) {
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (!isGranted) {
                    Toast.makeText(activity, "$permissionName 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    fun checkCameraPermissionAndOpen(onPermissionGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PERMISSION_GRANTED) {
            onPermissionGranted()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }
}

class CameraHandler(private val activity: ComponentActivity, private val onImageCaptured: (Bitmap) -> Unit) {
    private val takePictureLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                imageBitmap?.let { onImageCaptured(it) }
            }
        }

    fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(cameraIntent)
    }
}

class TextProcessor(private val activity: ComponentActivity, private val onTextExtracted: (String) -> Unit, private val onTranslationComplete: (Bitmap, String) -> Unit) {
    private val client = OkHttpClient()

    fun extractAndTranslateText(image: Bitmap) {
        val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(image, 0)
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                onTextExtracted(visionText.text)
                val translatedTexts = mutableListOf<Pair<String, android.graphics.Rect>>()

                visionText.textBlocks.forEach { block ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val translated = translateText(block.text)
                        if (translated != null) {
                            withContext(Dispatchers.Main) {
                                translatedTexts.add(Pair(translated, block.boundingBox!!))
                                if (translatedTexts.size == visionText.textBlocks.size) {
                                    overlayTranslatedText(image, translatedTexts, block)
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, "번역 실패: 네트워크 문제나 API 키 문제일 수 있습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(activity, "텍스트 추출 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun extractColorFromImage(image: Bitmap, rect: Rect): Int? {
        return try {
            // 사각형의 중앙 좌표 계산
            val centerX = rect.left + (rect.width() / 2)
            val centerY = rect.top + (rect.height() / 2)

            // 좌표가 이미지 범위 내에 있는지 확인
            if (centerX in 0 until image.width && centerY in 0 until image.height) {
                image.getPixel(centerX, centerY)
            } else {
                null
            }
        } catch (e: Exception) {
            // 예외 발생 시 null 반환 (예: 범위를 벗어남)
            null
        }
    }

    private fun translateText(text: String): String? {
        val apiKey = "AIzaSyDhag1OMnOMbkl2jEoepgCsWLGZdM0seeg"
        val url = "https://translation.googleapis.com/language/translate/v2?key=$apiKey&q=$text&target=ko"

        val request = Request.Builder().url(url).build()
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "")
                json.getJSONObject("data").getJSONArray("translations").getJSONObject(0).getString("translatedText")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun overlayTranslatedText(
        image: Bitmap,
        translatedTexts: List<Pair<String, Rect>>,
        originalTextBlock: com.google.mlkit.vision.text.Text.TextBlock
    ) {
        val mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            style = Paint.Style.FILL
        }

        translatedTexts.forEach { (text, rect) ->
            // 기존 텍스트 영역을 배경색으로 덮음
            paint.color = Color.WHITE
            canvas.drawRect(rect, paint)

            // 이미지에서 원래 텍스트의 색상을 추출
            val textColor = extractColorFromImage(image, rect)
            paint.color = textColor ?: Color.BLACK // 색상 추출 실패 시 기본값으로 검정색 사용

            // 원래 텍스트와 유사한 폰트 크기 설정
            val textSize = calculateFontSize(rect.height())
            paint.textSize = textSize

            // bounding box 내에서 텍스트 위치 조정 (텍스트의 정확한 위치와 일치하도록 수정)
            val xPosition = rect.left.toFloat()
            val yPosition = rect.top.toFloat() - paint.ascent()

            canvas.drawText(text, xPosition, yPosition, paint)
        }

        onTranslationComplete(mutableBitmap, translatedTexts.joinToString("\n") { it.first })
    }

    private fun calculateFontSize(rectHeight: Int): Float {
        // bounding box 높이를 기반으로 폰트 크기 계산
        return rectHeight * 0.9f // 약간의 여유 공간을 남기기 위해 0.9로 조정
    }

}

@androidx.compose.runtime.Composable
fun CameraTranslationScreen(
    capturedImage: Bitmap?,
    extractedText: String,
    translatedText: String,
    onOpenCamera: () -> Unit
) {
    // 수직 스크롤 추가
    val scrollState = androidx.compose.foundation.rememberScrollState()

    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState), // 스크롤 가능하게 설정
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Button(
            onClick = { onOpenCamera() },
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            androidx.compose.material3.Text(text = "카메라 열기")
        }

        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

        capturedImage?.let {
            androidx.compose.foundation.Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Captured Image",
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(8.dp)
                    .border(1.dp, androidx.compose.ui.graphics.Color.Gray)
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))

        androidx.compose.material3.Divider(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        androidx.compose.material3.Text(
            text = "추출된 텍스트:",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp)
        )

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp) // 텍스트가 길어지면 최대 높이 150dp까지 설정
                .padding(8.dp)
                .border(1.dp, androidx.compose.ui.graphics.Color.LightGray)
                .padding(8.dp)
        ) {
            item {
                androidx.compose.material3.Text(
                    text = extractedText,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(24.dp))

        androidx.compose.material3.Text(
            text = "번역된 텍스트:",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = androidx.compose.ui.Modifier.padding(bottom = 8.dp)
        )

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp) // 번역된 텍스트도 최대 높이 설정
                .padding(8.dp)
                .border(1.dp, androidx.compose.ui.graphics.Color.LightGray)
                .padding(8.dp)
        ) {
            item {
                androidx.compose.material3.Text(
                    text = translatedText,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}