package com.custom.redmitvlauncher.util

import android.app.Activity
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object RemoteCommandExecutor {

    /**
     * Thực thi lệnh shell / ADB nội bộ (yêu cầu quyền root hoặc system/adb shell)
     */
    fun runShellCommand(command: String): Boolean {
        return try {
            // Thử chạy qua su trước, nếu không có root thì chạy sh thường
            val process = try {
                Runtime.getRuntime().exec("su")
            } catch (e: Exception) {
                Runtime.getRuntime().exec("sh")
            }
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Tắt màn hình / Đưa TV vào chế độ Sleep bằng lệnh ADB KeyEvent 26 (POWER) hoặc 223 (SLEEP)
     */
    fun turnOffTv() {
        val success = runShellCommand("input keyevent 223")
        if (!success) {
            runShellCommand("input keyevent 26")
        }
    }

    /**
     * Chụp ảnh màn hình TV (Ưu tiên screencap hệ thống, fallback sang PixelCopy của Window)
     */
    fun captureScreenBase64(activity: Activity, callback: (String?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val view = activity.window.decorView.rootView
            val bitmap = Bitmap.createBitmap(
                view.width.coerceAtLeast(1),
                view.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            try {
                PixelCopy.request(
                    activity.window,
                    bitmap,
                    { copyResult ->
                        if (copyResult == PixelCopy.SUCCESS) {
                            // Nén kích thước nhỏ lại để đẩy qua Firebase Realtime DB siêu nhanh
                            val scaled = Bitmap.createScaledBitmap(bitmap, 640, 360, true)
                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            callback(base64)
                        } else {
                            callback(null)
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (e: Exception) {
                callback(null)
            }
        } else {
            callback(null)
        }
    }
}
