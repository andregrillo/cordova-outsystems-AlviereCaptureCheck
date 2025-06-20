package com.outsystems.alvierecapturecheck;

import android.content.Intent;
import android.os.Build;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.alviere.android.accounts.AccountsSdk;
import com.alviere.android.accounts.sdk.callback.DocumentCaptureSdkCallback;
import com.alviere.android.accounts.sdk.model.client.response.DocumentCaptureDetailsModel;
import com.alviere.android.accounts.sdk.model.common.DocumentTypeModel;

import com.alviere.android.payments.PaymentsSdk;
import com.alviere.android.payments.sdk.callback.CheckCaptureSdkCallback;
import com.alviere.android.payments.sdk.model.client.response.CheckCaptureDetailsModel;

import java.util.List;
import java.util.Map;

/**
 * This class echoes a string called from JavaScript.
 */
public class AlviereCaptureCheck extends CordovaPlugin {

    private CallbackContext callback;
    private CallbackContext captureCheckCallback;
    private CallbackContext captureDosierCallback;
    private static Boolean isAwaitingResponse = false;

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        PaymentsSdk.INSTANCE.init();
        AccountsSdk.INSTANCE.init();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        
        callback = callbackContext;
        if (action.equals("setCheckCallbacks")){
            captureCheckCallback = callbackContext;
            return true;
        }else if (action.equals("setDossierCallbacks")){
            captureDosierCallback = callbackContext;
            return true;
        }else if (action.equals("captureCheck")) {

            if(captureCheckCallback == null){
                callback.error("Callbacks were not set!");
                return false;
            }
            if(isAwaitingResponse){
                callbackContext.error("Awaiting Capture Response!");
                return false;
            }
            isAwaitingResponse = true;

            CheckCaptureSdkCallback checkCaptureSdkCallback = new CheckCaptureSdkCallback() {
                @Override
                public void onSuccess(@NonNull CheckCaptureDetailsModel checkCapture) {
                    JSONArray images = new JSONArray();
                    images.put(checkCapture.getEncodedFront());
                    images.put(checkCapture.getEncodedBack());
                    PluginResult result = new PluginResult(PluginResult.Status.OK,images);
                    result.setKeepCallback(true);
                    captureCheckCallback.sendPluginResult(result);
                    isAwaitingResponse = false;
                }

                @Override
                public void onEvent(@NonNull String event, @Nullable Map<String, String> metadata) {
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR,event);
                    result.setKeepCallback(true);
                    captureCheckCallback.sendPluginResult(result);
                    isAwaitingResponse = false;
                }
            };
            PaymentsSdk.INSTANCE.setEventListener(checkCaptureSdkCallback);
            Intent intent = PaymentsSdk.INSTANCE.checkCaptureByIntent(cordova.getActivity());
            isAwaitingResponse = true;
            cordova.getActivity().startActivity(intent);
            return true;
        }else if(action.equals("captureDossier")){

            if(captureDosierCallback == null){
                callback.error("Callbacks were not set!");
                return false;
            }
            if(isAwaitingResponse){
                callbackContext.error("Awaiting Capture Response!");
                return false;
            }
            //String docsJSON = args.getString(0);
            //JSONArray docs = new JSONArray(docsJSON);
            //if (docs == null || docs.length() == 0){
            //    callbackContext.error("Documents not specified!");
            //}
            JSONObject payload = args.getJSONObject(0);
            String accountUUID = payload.getString("accountUUID");
            String token       = payload.getString("token");
            JSONArray docs     = payload.getJSONArray("docTypes");
            if (docs == null || docs.length() == 0) {
                callbackContext.error("Documents not specified!");
                return false;
            }
            isAwaitingResponse = true;

            DocumentCaptureSdkCallback documentCaptureSdkCallback = new DocumentCaptureSdkCallback() {
                @Override
                public void onSuccess(@NonNull List<DocumentCaptureDetailsModel> dosierCaptures) {
                    JSONArray images = new JSONArray();
                    for (DocumentCaptureDetailsModel dosierCapture:dosierCaptures) {
                        JSONObject doc = new JSONObject();
                        try {
                            doc.put("image",dosierCapture.getEncodedImage());
                            doc.put("type",dosierCapture.getType());
                            images.put(doc);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    PluginResult result = new PluginResult(PluginResult.Status.OK,images);
                    result.setKeepCallback(true);
                    captureDosierCallback.sendPluginResult(result);
                    isAwaitingResponse = false;
                }

                @Override
                public void onEvent(@NonNull String event, @Nullable Map<String, String> metadata) {
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR,event);
                    result.setKeepCallback(true);
                    captureDosierCallback.sendPluginResult(result);
                    isAwaitingResponse = false;
                }
            };

            AccountsSdk.INSTANCE.setEventListener(documentCaptureSdkCallback);
            //DocumentTypeModel[] filesToCapture = new DocumentTypeModel[docs.length()];
            //for (int i = 0; i < docs.length(); i++) {
            //    filesToCapture[i] = DocumentTypeModel.valueOf(docs.getString(i));
            //}
            //Intent intent =  AccountsSdk.INSTANCE.documentCaptureByIntent(cordova.getActivity(),filesToCapture);
            // convert JSON array of doc‐type strings into SDK enum values
            DocumentTypeModel[] filesToCapture = new DocumentTypeModel[docs.length()];
            for (int i = 0; i < docs.length(); i++) {
                filesToCapture[i] = DocumentTypeModel.valueOf(docs.getString(i));
            }
            // (If the SDK needs the accountUUID/token, set them here on AccountsSdk)
            // e.g. AccountsSdk.INSTANCE.setAuth(accountUUID, token);
            Intent intent = AccountsSdk.INSTANCE.documentCaptureByIntent(
                                cordova.getActivity(),
                                filesToCapture
                           );
            cordova.getActivity().startActivity(intent);
            return true;
        }else if (action.equals("checkPermission")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    callback.sendPluginResult(new PluginResult(PluginResult.Status.OK,checkPermissionAction()));
                }
            });
            return true;
        } else if (action.equals("requestPermission")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        requestPermissionAction();
                    } catch (Exception e) {
                        e.printStackTrace();
                        callbackContext.error("Request permission has been denied.");
                        callback = null;
                    }
                }
            });
            return true;
        }
        callbackContext.error("Action not mapped!");
        callback = null;
        return false;
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (callback == null) {
            return;
        }

        JSONObject returnObj = new JSONObject();
        if (permissions != null && permissions.length > 0) {
            //Call checkPermission again to verify
            boolean hasPermission = checkPermissionAction();
            callback.sendPluginResult(new PluginResult(PluginResult.Status.OK,hasPermission));
        } else {
            callback.error("Unknown error.");
        }
        callback = null;
    }

    private boolean checkPermissionAction() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        } else {
            return cordova.hasPermission(Manifest.permission.CAMERA);
        }
    }

    private void requestPermissionAction() throws Exception {
        if (checkPermissionAction()) {
            callback.sendPluginResult(new PluginResult(PluginResult.Status.OK,true));
        } else {
            cordova.requestPermissions(this, 10001, new String[]{Manifest.permission.CAMERA});
        }
    }
}
