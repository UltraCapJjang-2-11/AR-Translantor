package com.example.text


import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {

    private val webSocketViewModel: WebSocketViewModel by viewModels() // ViewModel 초기화

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize()
            ) {
                // 권한 처리 및 카메라 프리뷰 표시
                CameraPreviewWithPermissions(viewModel = webSocketViewModel)
                webSocketViewModel.startAudioCapture()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewWithPermissions(viewModel: WebSocketViewModel) {
    // 확인할 권한 리스트
    val permissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    // 복수 권한 상태 기억
    val multiplePermissionsState = rememberMultiplePermissionsState(permissions = permissions)

    LaunchedEffect(Unit) {
        // 컴포지션이 처음 실행될 때 권한 요청
        multiplePermissionsState.launchMultiplePermissionRequest()
    }

    // 모든 권한 상태 확인
    val allPermissionsGranted = multiplePermissionsState.permissions.all { it.status.isGranted }

    val shouldShowRationale = multiplePermissionsState.permissions.any {
        !it.status.isGranted && it.status.shouldShowRationale
    }

    when {
        allPermissionsGranted -> {
            // 모든 권한 부여 시 카메라 프리뷰 표시
            CameraPreview()
            SpeechRecognitionScreen(viewModel)
        }
        shouldShowRationale -> {
            // 권한 설명 필요
            PermissionRationale(onRequestPermission = {
                multiplePermissionsState.launchMultiplePermissionRequest()
            })
        }
        else -> {
            // 권한이 거부된 경우
            PermissionDenied()
        }
    }
}

@Composable
fun SpeechRecognitionScreen(viewModel: WebSocketViewModel) {
    val receivedMessage by viewModel.receivedMessage.collectAsState() // ViewModel 상태 관찰

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 110.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (receivedMessage.isNotBlank()) receivedMessage else "No recognized text yet.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .background(color = Color.White) // 텍스트 배경을 흰색으로 설정
                .padding(2.dp) // 텍스트와 배경 사이에 여백 추가

        )
    }

}

@Composable
fun PermissionRationale(onRequestPermission: () -> Unit) {
    // 권한 요청 이유를 설명하고 다시 요청하는 UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("이 앱을 사용하려면 카메라 및 오디오 녹음 권한이 필요합니다.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRequestPermission) {
            Text("권한 요청")
        }
    }
}

@Composable
fun PermissionDenied() {
    // 권한이 거부된 경우 사용자에게 알리는 UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("카메라 또는 오디오 녹음 권한이 거부되었습니다. 설정에서 권한을 변경해주세요.", style = MaterialTheme.typography.bodyLarge)
    }
}
