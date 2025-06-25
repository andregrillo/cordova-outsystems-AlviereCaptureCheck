package com.outsystems.alvierecapturecheck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import com.alviere.android.payments.ui.client.CheckCaptureScreen

class CheckCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val cameraToken = intent.extras?.getString(CAMERA_TOKEN) ?: ""
            MaterialTheme {
                CheckCaptureScreen(
                    onCloseAction = ::onResultCheck,
                    cameraToken = cameraToken
                )
            }
        }
    }

    private fun onResultCheck() {
        val result = Intent()
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        private const val CAMERA_TOKEN = "CAMERA_TOKEN"
        fun newInstance(context: Context, cameraToken: String) =
            Intent(context, CheckCaptureActivity::class.java)
                .putExtra(CAMERA_TOKEN, cameraToken)
    }
}