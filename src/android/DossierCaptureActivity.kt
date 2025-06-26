package com.outsystems.alvierecapturecheck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import android.content.pm.ActivityInfo
import com.alviere.android.accounts.sdk.model.common.DocumentTypeModel
import com.alviere.android.accounts.ui.client.DocumentCaptureScreen

class DossierCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // lock this screen to portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            val cameraToken = intent.extras?.getString(CAMERA_TOKEN) ?: ""

            // Retrieve the list of document type names from the Intent
            val docTypeNames = intent.getStringArrayListExtra(EXTRA_DOC_TYPES) ?: arrayListOf()

            // Convert each name into the corresponding SDK enum
            val documentsToCapture = docTypeNames.map { name ->
                DocumentTypeModel.valueOf(name)
            }

            MaterialTheme {
                DocumentCaptureScreen(
                    documentsToCapture = documentsToCapture,
                    cameraToken = cameraToken,
                    onCloseAction = ::onResultCheck,
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
        private const val EXTRA_DOC_TYPES = "EXTRA_DOC_TYPES"
        fun newInstance(context: Context, cameraToken: String, docTypes: ArrayList<String>) =
            Intent(context, DossierCaptureActivity::class.java).apply {
                putExtra(CAMERA_TOKEN, cameraToken)
                putStringArrayListExtra(EXTRA_DOC_TYPES, docTypes)
            }
    }
}