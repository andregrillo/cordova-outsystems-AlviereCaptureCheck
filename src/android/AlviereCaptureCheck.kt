package com.outsystems.alvierecapturecheck

import android.Manifest
import android.os.Build
import com.alviere.android.accounts.AccountsSdk
import com.alviere.android.accounts.sdk.callback.DocumentCaptureSdkCallback
import com.alviere.android.accounts.sdk.model.client.response.DocumentCaptureDetailsModel
import com.alviere.android.accounts.sdk.model.common.DocumentTypeModel
import com.alviere.android.alcore.network.token.CameraTokenRequest
import com.alviere.android.alcore.network.token.TokenRepository
import com.alviere.android.payments.PaymentsSdk
import com.alviere.android.payments.sdk.callback.CheckCaptureSdkCallback
import com.alviere.android.payments.sdk.model.client.response.CheckCaptureDetailsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * This class echoes a string called from JavaScript.
 */
class AlviereCaptureCheck : CordovaPlugin() {

    private var callback: CallbackContext? = null
    private var captureCheckCallback: CallbackContext? = null
    private var captureDosierCallback: CallbackContext? = null
    private val tokenRepository = TokenRepository()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    @Throws(JSONException::class)
    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        callback = callbackContext
        if (action == "setCheckCallbacks") {
            captureCheckCallback = callbackContext
            return true
        } else if (action == "setDossierCallbacks") {
            captureDosierCallback = callbackContext
            return true
        } else if (action == "captureCheck") {
            if (captureCheckCallback == null) {
                callback!!.error("Callbacks were not set!")
                return false
            }
            if (isAwaitingResponse) {
                callbackContext.error("Awaiting Capture Response!")
                return false
            }
            isAwaitingResponse = true

            val checkCaptureSdkCallback: CheckCaptureSdkCallback =
                object : CheckCaptureSdkCallback {
                    override fun onSuccess(checkCapture: List<CheckCaptureDetailsModel>) {
                        val images = JSONArray()

                        for (captures in checkCapture) {
                            val doc = JSONObject()
                            try {
                                doc.put("image", captures.file)
                                doc.put("type", captures.documentType.name)
                                images.put(doc)
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        }

                        val result = PluginResult(PluginResult.Status.OK, images)
                        result.keepCallback = true
                        captureCheckCallback!!.sendPluginResult(result)
                        isAwaitingResponse = false
                    }

                    override fun onEvent(
                        event: String,
                        metadata: Map<String, String>?
                    ) {
                        val result = PluginResult(PluginResult.Status.ERROR, event)
                        result.keepCallback = true
                        captureCheckCallback!!.sendPluginResult(result)
                        isAwaitingResponse = false
                    }
                }

            PaymentsSdk.setEventListener(checkCaptureSdkCallback)
            isAwaitingResponse = true

            // Get variables
            val accountUUID = args.getString(0)
            val sessionToken = args.getString(1)

            scope.launch(Dispatchers.IO) {
                val tokenResult = tokenRepository.getCameraToken(sessionToken = sessionToken, body = CameraTokenRequest(accountUuid = accountUUID))

                val token = tokenResult.toString()
                    .substringAfter("data=")
                    .substringBefore(")")

                cordova.getActivity().startActivity(CheckCaptureActivity.newInstance(cordova.context, token.toString()))
            }
            return true
        } else if (action == "captureDossier") {
            if (captureDosierCallback == null) {
                callback!!.error("Callbacks were not set!")
                return false
            }
            if (isAwaitingResponse) {
                callbackContext.error("Awaiting Capture Response!")
                return false
            }

            val objectData = args.getJSONObject(0)
            val accountUUID = objectData.getString("accountUUID")
            val token = objectData.getString("token")
            val docs = objectData.getJSONArray("docTypes")
            if (docs == null || docs.length() == 0) {
                callbackContext.error("Documents not specified!")
                return false
            }
            isAwaitingResponse = true

            val documentCaptureSdkCallback: DocumentCaptureSdkCallback =
                object : DocumentCaptureSdkCallback {
                    override fun onSuccess(dosierCaptures: List<DocumentCaptureDetailsModel>) {
                        val images = JSONArray()
                        for (dosierCapture in dosierCaptures) {
                            val doc = JSONObject()
                            try {
                                doc.put("image", dosierCapture.file)
                                doc.put("type", dosierCapture.documentType.name)
                                images.put(doc)
                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        }
                        val result = PluginResult(PluginResult.Status.OK, images)
                        result.keepCallback = true
                        captureDosierCallback!!.sendPluginResult(result)
                        isAwaitingResponse = false
                    }

                    override fun onEvent(
                        event: String,
                        metadata: Map<String, String>?
                    ) {
                        val result = PluginResult(PluginResult.Status.ERROR, event)
                        result.keepCallback = true
                        captureDosierCallback!!.sendPluginResult(result)
                        isAwaitingResponse = false
                    }
                }

            AccountsSdk.setEventListener(documentCaptureSdkCallback)
            //DocumentTypeModel[] filesToCapture = new DocumentTypeModel[docs.length()];
            //for (int i = 0; i < docs.length(); i++) {
            //    filesToCapture[i] = DocumentTypeModel.valueOf(docs.getString(i));
            //}
            //Intent intent =  AccountsSdk.INSTANCE.documentCaptureByIntent(cordova.getActivity(),filesToCapture);
            // convert JSON array of doc‐type strings into SDK enum values
            val filesToCapture = arrayListOf<DocumentTypeModel>()
            for (i in 0..<docs.length()) {
                filesToCapture.add(i, DocumentTypeModel.valueOf(docs.getString(i)))
            }
            // (If the SDK needs the accountUUID/token, set them here on AccountsSdk)
            // e.g. AccountsSdk.INSTANCE.setAuth(accountUUID, token);

            scope.launch(Dispatchers.IO) {
                val tokenResult = tokenRepository.getCameraToken(sessionToken = token, body = CameraTokenRequest(accountUuid = accountUUID))

                val token = tokenResult.toString()
                    .substringAfter("data=")
                    .substringBefore(")")

                cordova.getActivity().startActivity(DossierCaptureActivity.newInstance(cordova.context, token.toString()))
            }
            isAwaitingResponse = true
            return true
        } else if (action == "checkPermission") {
            cordova.getThreadPool().execute(object : Runnable {
                override fun run() {
                    callback!!.sendPluginResult(
                        PluginResult(
                            PluginResult.Status.OK,
                            checkPermissionAction()
                        )
                    )
                }
            })
            return true
        } else if (action == "requestPermission") {
            cordova.getThreadPool().execute(object : Runnable {
                override fun run() {
                    try {
                        requestPermissionAction()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callbackContext.error("Request permission has been denied.")
                        callback = null
                    }
                }
            })
            return true
        }
        callbackContext.error("Action not mapped!")
        callback = null

        return false
    }

    @Throws(JSONException::class)
    override fun onRequestPermissionResult(
        requestCode: Int,
        permissions: Array<String?>?,
        grantResults: IntArray?
    ) {
        if (callback == null) {
            return
        }

        val returnObj = JSONObject()
        if (permissions != null && permissions.size > 0) {
            //Call checkPermission again to verify
            val hasPermission = checkPermissionAction()
            callback!!.sendPluginResult(PluginResult(PluginResult.Status.OK, hasPermission))
        } else {
            callback!!.error("Unknown error.")
        }
        callback = null
    }

    private fun checkPermissionAction(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        } else {
            return cordova.hasPermission(Manifest.permission.CAMERA)
        }
    }

    @Throws(Exception::class)
    private fun requestPermissionAction() {
        if (checkPermissionAction()) {
            callback!!.sendPluginResult(PluginResult(PluginResult.Status.OK, true))
        } else {
            cordova.requestPermissions(this, 10001, arrayOf<String>(Manifest.permission.CAMERA))
        }
    }

    companion object {
        private var isAwaitingResponse = false
    }
}