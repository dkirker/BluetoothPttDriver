package com.openmobl.pttDriver.utils

import android.content.*
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnPreparedListener
import android.os.Build
import android.util.Log
import java.io.IOException

object SoundUtils {
    private val TAG = SoundUtils::class.java.name
    private const val DEFAULT_VOL = 0.6f

    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     * @param audioDevice The audio device address to play the sound resource on
     */
    @Throws(IOException::class)
    fun playSoundResource(context: Context, resId: Int, audioDevice: String?) {
        if (audioDevice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Log.v(TAG, "Attempt playSoundResource to mac $audioDevice")
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            var selectedAudioDevice: AudioDeviceInfo? = null
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.address == audioDevice) {
                    selectedAudioDevice = device
                }
            }
            playSoundResource(context, resId, selectedAudioDevice, DEFAULT_VOL, DEFAULT_VOL)
        } else {
            playSoundResource(context, resId, null, DEFAULT_VOL, DEFAULT_VOL)
        }
    }
    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     * @param leftVol Left volume level (0.0f - 1.0f)
     * @param rightVol Right volume level (0.0f - 1.0f)
     */
    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun playSoundResource(
        context: Context, resId: Int,
        leftVol: Float = DEFAULT_VOL, rightVol: Float = DEFAULT_VOL
    ) {
        playSoundResource(context, resId, null, leftVol, rightVol)
    }
    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     * @param audioDevice The audio device to play the sound resource on
     * @param leftVol Left volume level (0.0f - 1.0f)
     * @param rightVol Right volume level (0.0f - 1.0f)
     */
    /**
     * Play a sound resource based on resource ID.
     * @param context The application context containing the resource
     * @param resId The resource ID to play
     * @param audioDevice The audio device to play the sound resource on
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun playSoundResource(
        context: Context, resId: Int, audioDevice: AudioDeviceInfo?,
        leftVol: Float = DEFAULT_VOL, rightVol: Float = DEFAULT_VOL
    ) {
        val resFd = context.resources.openRawResourceFd(resId)
        if (resFd != null) {
            val completionListener = OnCompletionListener { mp ->
                mp.release()
                try {
                    resFd.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val preparedListener = OnPreparedListener { mp -> mp.start() }
            val mPlayer = MediaPlayer()

            /*
                // TODO: Handle gracefully and only if device is SCO and not A2DP
                AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                audioManager.setMode(0);
                audioManager.setBluetoothScoOn(true);
                audioManager.startBluetoothSco();
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
             */if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            mPlayer.setOnCompletionListener(completionListener)
            mPlayer.setOnPreparedListener(preparedListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (audioDevice != null) {
                    Log.v(TAG, "audioDevice = $audioDevice")
                    Log.v(TAG, "audioDevice.id = " + audioDevice.id)
                    Log.v(TAG, "audioDevice.type = " + audioDevice.type)
                    Log.v(TAG, "audioDevice.productName = " + audioDevice.productName)
                    Log.v(TAG, "audioDevice.address = " + audioDevice.address)
                }
                mPlayer.preferredDevice = audioDevice
            }
            mPlayer.setDataSource(
                resFd.fileDescriptor,
                resFd.startOffset, resFd.length
            )
            mPlayer.setVolume(leftVol, rightVol)
            mPlayer.prepareAsync()
            //mPlayer.start();
        }
    }
}