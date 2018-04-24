package com.rnim.rn.audio;

import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

public class AudioRecorder {

    private static final String STATUS = "status";
    private static final String STATUS_OK = "OK";
    private static final String AUDIO_FILE_URL = "audioFileURL";
    private static final String FILE_TYPE_PREFIX = "file://";

    private MediaRecorder recorder;
    private String recordingPath;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private Timer timer;
    private StopWatch stopWatch;
    private EventListener eventListener;

    private boolean isPauseResumeCapable = false;
    private Method pauseMethod = null;
    private Method resumeMethod = null;

    AudioRecorder(String recordingPath, EventListener eventListener) {
        this.recordingPath = recordingPath;
        this.stopWatch = new StopWatch();
        this.eventListener = eventListener;
        this.isPauseResumeCapable = Build.VERSION.SDK_INT > Build.VERSION_CODES.M;
        if (this.isPauseResumeCapable) {
            try {
                this.pauseMethod = MediaRecorder.class.getMethod("pause");
                this.resumeMethod = MediaRecorder.class.getMethod("resume");
            } catch (NoSuchMethodException e) {
                Log.d("ERROR", "Failed to get a reference to pause and/or resume method");
            }
        }
    }

    public void prepare(ReadableMap recordingSettings) throws Exception {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        int outputFormat = getOutputFormatFromString(recordingSettings.getString("OutputFormat"));
        recorder.setOutputFormat(outputFormat);
        int audioEncoder = getAudioEncoderFromString(recordingSettings.getString("AudioEncoding"));
        recorder.setAudioEncoder(audioEncoder);
        recorder.setAudioSamplingRate(recordingSettings.getInt("SampleRate"));
        recorder.setAudioChannels(recordingSettings.getInt("Channels"));
        recorder.setAudioEncodingBitRate(recordingSettings.getInt("AudioEncodingBitRate"));
        recorder.setOutputFile(recordingPath);
        recorder.prepare();
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isPaused() {
        return isPaused;
    }

    private int getAudioEncoderFromString(String audioEncoder) {
        switch (audioEncoder) {
            case "aac":
                return MediaRecorder.AudioEncoder.AAC;
            case "aac_eld":
                return MediaRecorder.AudioEncoder.AAC_ELD;
            case "amr_nb":
                return MediaRecorder.AudioEncoder.AMR_NB;
            case "amr_wb":
                return MediaRecorder.AudioEncoder.AMR_WB;
            case "he_aac":
                return MediaRecorder.AudioEncoder.HE_AAC;
            case "vorbis":
                return MediaRecorder.AudioEncoder.VORBIS;
            default:
                Log.d("INVALID_AUDIO_ENCODER",
                        "USING MediaRecorder.AudioEncoder.DEFAULT instead of " + audioEncoder + ": " +
                                MediaRecorder.AudioEncoder.DEFAULT);
                return MediaRecorder.AudioEncoder.DEFAULT;
        }
    }

    private int getOutputFormatFromString(String outputFormat) {
        switch (outputFormat) {
            case "mpeg_4":
                return MediaRecorder.OutputFormat.MPEG_4;
            case "aac_adts":
                return MediaRecorder.OutputFormat.AAC_ADTS;
            case "amr_nb":
                return MediaRecorder.OutputFormat.AMR_NB;
            case "amr_wb":
                return MediaRecorder.OutputFormat.AMR_WB;
            case "three_gpp":
                return MediaRecorder.OutputFormat.THREE_GPP;
            case "webm":
                return MediaRecorder.OutputFormat.WEBM;
            default:
                Log.d("INVALID_OUPUT_FORMAT",
                        "USING MediaRecorder.OutputFormat.DEFAULT : " + MediaRecorder.OutputFormat.DEFAULT);
                return MediaRecorder.OutputFormat.DEFAULT;

        }
    }

    public void startRecording() {
        recorder.start();
        stopWatch.reset();
        stopWatch.start();
        isRecording = true;
        isPaused = false;
        startTimer();
    }

    private void startTimer() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isPaused) {
                    WritableMap body = Arguments.createMap();
                    body.putDouble("currentTime", stopWatch.getTimeSeconds());
                    eventListener.onEvent("recordingProgress", body);
                }
            }
        }, 0, 1000);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public void stopRecording() throws RuntimeException {
        stopTimer();
        isRecording = false;
        isPaused = false;
        recorder.stop();
        recorder.release();
        stopWatch.stop();
        recorder = null;
        WritableMap result = Arguments.createMap();
        result.putString(STATUS, STATUS_OK);
        result.putString(AUDIO_FILE_URL, FILE_TYPE_PREFIX + recordingPath);
        eventListener.onEvent("recordingFinished", result);
    }

    public void pauseRecording() throws RuntimeException, IllegalAccessException, InvocationTargetException {
        pauseMethod.invoke(recorder);
        stopWatch.stop();
        isPaused = true;
    }

    public boolean isPauseCapable() {
        return isPauseResumeCapable && null != pauseMethod;
    }

    public boolean isResumeCapable() {
        return isPauseResumeCapable && null != resumeMethod;
    }

    public void resumeRecording() throws RuntimeException, IllegalAccessException, InvocationTargetException {
        resumeMethod.invoke(recorder);
        stopWatch.start();
        isPaused = false;
    }

    public interface EventListener {

        void onEvent(String eventName, Object params);

    }

}
