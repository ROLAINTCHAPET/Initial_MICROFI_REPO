package com.microfi.microfi_mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * "Contact Branch" (core/contact_branch.dart) needs to place a call directly — ACTION_CALL —
 * rather than handing off to the device's own Phone app pre-filled with the number (ACTION_DIAL,
 * what url_launcher's plain tel: scheme does, requiring the agent to tap Call again themselves).
 * No Flutter plugin in this project's dependency tree wraps ACTION_CALL, so this one small native
 * method channel exists to do exactly that — CALL_PHONE is a dangerous permission Dart already
 * requests via permission_handler before ever invoking this channel, but the manifest-declared
 * permission is re-checked here too since a native ACTION_CALL without it throws a SecurityException.
 */
class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.microfi.microfi_mobile/phone"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "placeCall") {
                val number = call.argument<String>("number")
                if (number.isNullOrBlank()) {
                    result.error("INVALID_NUMBER", "No phone number provided", null)
                    return@setMethodCallHandler
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                        != PackageManager.PERMISSION_GRANTED) {
                    result.error("PERMISSION_DENIED", "CALL_PHONE not granted", null)
                    return@setMethodCallHandler
                }
                try {
                    startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
                    result.success(true)
                } catch (e: Exception) {
                    result.error("CALL_FAILED", e.message, null)
                }
            } else {
                result.notImplemented()
            }
        }
    }
}
