/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.artranslate.ktorclient.helpers

import android.app.Activity
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException

/**
 * Manages an ARCore Session using the Android Lifecycle API. Before starting a Session, this class
 * requests installation of Google Play Services for AR if it's not installed or not up to date and
 * asks the user for required permissions if necessary.
 */
class ARCoreSessionLifecycleHelper(
  val activity: Activity,
  val features: Set<Session.Feature> = setOf()
) : DefaultLifecycleObserver {
  var installRequested = false
  var session: Session? = null
    private set

  /**
   * Creating a session may fail. In this case, session will remain null, and this function will be
   * called with an exception.
   *
   * See
   * [the `Session` constructor](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#Session(android.content.Context)
   * ) for more details.
   */
  var exceptionCallback: ((Exception) -> Unit)? = null

  /**
   * Before `Session.resume()` is called, a session must be configured. Use
   * [`Session.configure`](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#configure-config)
   * or
   * [`setCameraConfig`](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#setCameraConfig-cameraConfig)
   */
  var beforeSessionResume: ((Session) -> Unit)? = null

  /**
   * 세션 생성을 시도합니다. Google Play Services for AR이 설치되지 않았거나 최신 버전이 아닌 경우,
   * 설치를 요청합니다.
   *
   * @return 세션 생성이 성공하면 `Session` 객체를 반환하고, 실패하면 `null`을 반환합니다.
   * 예외 발생 시 `exceptionCallback`을 호출하여 예외를 전달합니다.
   */
  private fun tryCreateSession(): Session? {
    // 카메라 권한이 부여되어야 합니다. 권한이 없는 경우 요청합니다.
    if (!CameraPermissionHelper.hasCameraPermission(activity)) {
      CameraPermissionHelper.requestCameraPermission(activity)
      return null
    }

    return try {
      // 필요한 경우 설치 요청을 합니다.
      when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
          installRequested = true  // 설치가 요청되었음을 기록
          // 설치 요청 후 tryCreateSession이 다시 호출되므로 현재는 null을 반환합니다.
          return null
        }
        ArCoreApk.InstallStatus.INSTALLED -> {
          // 설치가 완료된 경우 추가 작업은 필요하지 않습니다.
        }
      }

      // Google Play Services for AR이 설치되고 최신 상태일 때 세션을 생성합니다.
      Session(activity, features)
    } catch (e: Exception) {
      exceptionCallback?.invoke(e)  // 예외 발생 시 콜백을 통해 예외 전달
      null
    }
  }

  /**
   * 액티비티가 재개될 때 세션을 활성화합니다.
   * 세션을 구성한 후 `resume()` 메서드를 호출하여 세션을 재개합니다.
   */
  override fun onResume(owner: LifecycleOwner) {
    val session = this.session ?: tryCreateSession() ?: return
    try {
      beforeSessionResume?.invoke(session)
      session.resume()
      this.session = session
    } catch (e: CameraNotAvailableException) {
      exceptionCallback?.invoke(e)
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    session?.pause()
  }

  override fun onDestroy(owner: LifecycleOwner) {
    // Explicitly close the ARCore session to release native resources.
    // Review the API reference for important considerations before calling close() in apps with
    // more complicated lifecycle requirements:
    // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
    session?.close()
    session = null
  }

  fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    results: IntArray
  ) {
    if (!CameraPermissionHelper.hasCameraPermission(activity)) {
      // Use toast instead of snackbar here since the activity will exit.
      Toast.makeText(
          activity,
          "Camera permission is needed to run this application",
          Toast.LENGTH_LONG
        )
        .show()
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(activity)) {
        // Permission denied with checking "Do not ask again".
        CameraPermissionHelper.launchPermissionSettings(activity)
      }
      activity.finish()
    }
  }
}
