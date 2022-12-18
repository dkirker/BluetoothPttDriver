package com.openmobl.pttDriver.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import java.io.IOException;

public class SoundUtils {
    private static final String TAG = SoundUtils.class.getName();

    private static final float DEFAULT_VOL = 1.0f;

    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     */
    public static void playSoundResource(Context context, int resId) throws IOException {
        playSoundResource(context, resId, DEFAULT_VOL, DEFAULT_VOL);
    }

    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     * @param audioDevice The audio device to play the sound resource on
     */
    public static void playSoundResource(Context context, int resId, AudioDeviceInfo audioDevice) throws IOException {
        playSoundResource(context, resId, audioDevice, DEFAULT_VOL, DEFAULT_VOL);
    }

    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     * @param audioDevice The audio device address to play the sound resource on
     */
    public static void playSoundResource(Context context, int resId, String audioDevice) throws IOException {
        if (audioDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.v(TAG, "Attempt playSoundResource to mac " + audioDevice);
            AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo selectedAudioDevice = null;

            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                if (device.getAddress().equals(audioDevice)) {
                    selectedAudioDevice = device;
                }
            }

            playSoundResource(context, resId, selectedAudioDevice, DEFAULT_VOL, DEFAULT_VOL);
        } else {
            playSoundResource(context, resId, null, DEFAULT_VOL, DEFAULT_VOL);
        }
    }

    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     * @param leftVol Left volume level (0.0f - 1.0f)
     * @param rightVol Right volume level (0.0f - 1.0f)
     */
    public static void playSoundResource(Context context, int resId,
                                         float leftVol, float rightVol) throws IOException {
        playSoundResource(context, resId, null, leftVol, rightVol);
    }


    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     * @param audioDevice The audio device to play the sound resource on
     * @param leftVol Left volume level (0.0f - 1.0f)
     * @param rightVol Right volume level (0.0f - 1.0f)
     */
    public static void playSoundResource(Context context, int resId, AudioDeviceInfo audioDevice,
                                         float leftVol, float rightVol) throws IOException {
        AssetFileDescriptor resFd = context.getResources().openRawResourceFd(resId);

        if (resFd != null) {
            MediaPlayer.OnCompletionListener completionListener = new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp){
                    mp.release();
                    try {
                        resFd.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            MediaPlayer.OnPreparedListener preparedListener = new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp){
                    mp.start();
                }
            };
            MediaPlayer mPlayer = new MediaPlayer();

            /*
                // TODO: Handle gracefully and only if device is SCO and not A2DP
                AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                audioManager.setMode(0);
                audioManager.setBluetoothScoOn(true);
                audioManager.startBluetoothSco();
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            }
            mPlayer.setOnCompletionListener(completionListener);
            mPlayer.setOnPreparedListener(preparedListener);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (audioDevice != null) {
                    Log.v(TAG, "audioDevice = " + audioDevice);

                    Log.v(TAG, "audioDevice.id = " + audioDevice.getId());
                    Log.v(TAG, "audioDevice.type = " + audioDevice.getType());
                    Log.v(TAG, "audioDevice.productName = " + audioDevice.getProductName());
                    Log.v(TAG, "audioDevice.address = " + audioDevice.getAddress());
                }
                mPlayer.setPreferredDevice(audioDevice);
            }
            mPlayer.setDataSource(resFd.getFileDescriptor(),
                    resFd.getStartOffset(), resFd.getLength());
            mPlayer.setVolume(leftVol, rightVol);
            mPlayer.prepareAsync();
            //mPlayer.start();
        }
    }
}