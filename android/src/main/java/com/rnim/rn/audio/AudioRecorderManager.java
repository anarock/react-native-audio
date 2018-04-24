package com.rnim.rn.audio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

class AudioRecorderManager extends ReactContextBaseJavaModule implements AudioRecorder.EventListener {

    private static final String TAG = "ReactNativeAudio";

    private static final String DocumentDirectoryPath = "DocumentDirectoryPath";
    private static final String PicturesDirectoryPath = "PicturesDirectoryPath";
    private static final String MainBundlePath = "MainBundlePath";
    private static final String CachesDirectoryPath = "CachesDirectoryPath";
    private static final String LibraryDirectoryPath = "LibraryDirectoryPath";
    private static final String MusicDirectoryPath = "MusicDirectoryPath";
    private static final String DownloadsDirectoryPath = "DownloadsDirectoryPath";

    private HashMap<String, AudioRecorder> recorders;

    AudioRecorderManager(ReactApplicationContext reactContext) {
        super(reactContext);
        recorders = new HashMap<>();
    }

    @Override
    public Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();
        constants.put(DocumentDirectoryPath, this.getReactApplicationContext().getFilesDir().getAbsolutePath());
        constants.put(PicturesDirectoryPath,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
        constants.put(MainBundlePath, "");
        constants.put(CachesDirectoryPath, this.getReactApplicationContext().getCacheDir().getAbsolutePath());
        constants.put(LibraryDirectoryPath, "");
        constants.put(MusicDirectoryPath,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath());
        constants.put(DownloadsDirectoryPath,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        return constants;
    }

    @Override
    public String getName() {
        return AudioRecorderManager.class.getSimpleName();
    }

    @ReactMethod
    public void checkAuthorizationStatus(Promise promise) {
        promise.resolve(ContextCompat.checkSelfPermission(getReactApplicationContext(),
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }

    @ReactMethod
    public void prepareRecordingAtPath(String recordingPath, ReadableMap recordingSettings, Promise promise) {
        if (isRecordingGoingOn()) {
            logAndRejectPromise(promise, IllegalStateException.class.getSimpleName(),
                    "Please call stopRecording before starting recording");
            return;
        }
        AudioRecorder recorder = new AudioRecorder(recordingPath, this);
        recorders.put(recordingPath, recorder);
        try {
            recorder.prepare(recordingSettings);
            promise.resolve(recordingPath);
        } catch (Exception e) {
            logAndRejectPromise(promise, e.getClass().getSimpleName(), "Make sure you've added RECORD_AUDIO " +
                    "permission to your AndroidManifest.xml file " + e.getMessage());
        }
    }

    @ReactMethod
    public void startRecording(String filePath, Promise promise) {
        AudioRecorder recorder = recorders.get(filePath);
        if (recorder == null) {
            logAndRejectPromise(promise, IllegalStateException.class.getSimpleName(),
                    "Please call prepareRecordingAtPath before starting recording");
            return;
        }
        if (recorder.isRecording()) {
            logAndRejectPromise(promise, IllegalStateException.class.getSimpleName(),
                    "Please call stopRecording before starting recording");
            return;
        }
        recorder.startRecording();
        promise.resolve(null);
    }

    @ReactMethod
    public void stopRecording(String filePath, Promise promise) {
        if (!isRecordingGoingOn()) {
            logAndRejectPromise(promise, IllegalStateException.class.getSimpleName(),
                    "Please call startRecording before stopping recording");
            return;
        }
        AudioRecorder recorder = recorders.get(filePath);
        try {
            recorder.stopRecording();
        } catch (Exception e) {
            logAndRejectPromise(promise, RuntimeException.class.getSimpleName(),
                    "No valid audio data received. You may be using a device that can't record audio.");
            return;
        }
        promise.resolve(null);
    }

    @ReactMethod
    public void pauseRecording(String filePath, Promise promise) {
        AudioRecorder recorder = recorders.get(filePath);
        if (!recorder.isPauseCapable()) {
            logAndRejectPromise(promise, RuntimeException.class.getSimpleName(),
                    "Method not available on this version of Android.");
            return;
        }
        if (!isRecordingPaused()) {
            try {
                recorder.pauseRecording();
            } catch (Exception e) {
                logAndRejectPromise(promise, RuntimeException.class.getSimpleName(),
                        "Method not available on this version of Android.");
                return;
            }
        }
        promise.resolve(null);
    }

    @ReactMethod
    public void resumeRecording(String filePath, Promise promise) {
        AudioRecorder recorder = recorders.get(filePath);
        if (!recorder.isResumeCapable()) {
            logAndRejectPromise(promise, RuntimeException.class.getSimpleName(),
                    "Method not available on this version of Android.");
            return;
        }
        if (recorder.isPaused()) {
            try {
                recorder.resumeRecording();
            } catch (InvocationTargetException | RuntimeException | IllegalAccessException e) {
                logAndRejectPromise(promise, RuntimeException.class.getSimpleName(),
                        "Method not available on this version of Android.");
                return;
            }
        }
        promise.resolve(null);
    }

    @Override
    public void onEvent(String eventName, Object params) {
        getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private boolean isRecordingGoingOn() {
        for (String filePath : recorders.keySet()) {
            if (recorders.get(filePath).isRecording()) {
                return true;
            }
        }
        return false;
    }

    private boolean isRecordingPaused() {
        for (String filePath : recorders.keySet()) {
            if (recorders.get(filePath).isPaused())
                return true;
        }
        return false;
    }

    private void logAndRejectPromise(Promise promise, String errorCode, String errorMessage) {
        Log.e(TAG, errorMessage);
        promise.reject(errorCode, errorMessage);
    }
}
