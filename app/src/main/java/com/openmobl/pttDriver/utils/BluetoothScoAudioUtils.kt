package com.openmobl.pttDriver.utils

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothProfile.ServiceListener
import android.content.Context
import android.media.AudioManager
import android.util.Log

object BluetoothScoAudioUtils {
    private val TAG = BluetoothScoAudioUtils::class.java.name
    fun internal_setActiveDevice(
        headsetProxy: BluetoothHeadset,
        device: BluetoothDevice?
    ): Boolean {
        return try {
            val argTypes = arrayOf<Class<*>>(
                BluetoothDevice::class.java
            )
            val m = headsetProxy.javaClass.getMethod("setActiveDevice", *argTypes)
            val result = m.invoke(headsetProxy, device) as Boolean
            Log.v(TAG, "internal_setActiveDevice $result")
            result
        } catch (e: Exception) {
            Log.d(TAG, "internal_setActiveDevice threw exception $e")
            false
        }
    }

    fun internal_setPriority(
        headsetProxy: BluetoothHeadset,
        device: BluetoothDevice?,
        priority: Int
    ): Boolean {
        return try {
            val argTypes = arrayOf(
                BluetoothDevice::class.java, Int::class.javaPrimitiveType
            )
            val m = headsetProxy.javaClass.getMethod("setPriority", *argTypes)
            val result = m.invoke(headsetProxy, device, priority) as Boolean
            Log.v(TAG, "internal_setPriority $result")
            result
        } catch (e: Exception) {
            Log.d(TAG, "internal_setPriority threw exception $e")
            false
        }
    }

    fun internal_connectAudio(headsetProxy: BluetoothHeadset): Boolean {
        return try {
            val argTypes = arrayOf<Class<*>>()
            val m = headsetProxy.javaClass.getMethod("connectAudio", *null as Array<Class<*>?>?)
            val result = m.invoke(headsetProxy) as Boolean
            Log.v(TAG, "internal_connectAudio $result")
            result
        } catch (e: Exception) {
            Log.d(TAG, "internal_connectAudio threw exception $e")
            false
        }
    }

    fun internal_setForceScoAudio(headsetProxy: BluetoothHeadset, forced: Boolean) {
        try {
            val argTypes = arrayOf<Class<*>?>(Boolean::class.javaPrimitiveType)
            val m = headsetProxy.javaClass.getMethod("setForceScoAudio", *argTypes)
            m.invoke(headsetProxy, forced)
            Log.v(TAG, "internal_setForceScoAudio")
        } catch (e: Exception) {
            Log.d(TAG, "internal_setForceScoAudio threw exception $e")
        }
    }

    fun internal_setAudioRouteAllowed(headsetProxy: BluetoothHeadset, allowed: Boolean) {
        try {
            val argTypes = arrayOf<Class<*>?>(Boolean::class.javaPrimitiveType)
            val m = headsetProxy.javaClass.getMethod("setAudioRouteAllowed", *argTypes)
            m.invoke(headsetProxy, allowed)
            Log.v(TAG, "internal_setAudioRouteAllowed")
        } catch (e: Exception) {
            Log.d(TAG, "internal_setAudioRouteAllowed threw exception $e")
        }
    }

    fun giveDevicePriority(context: Context, device: BluetoothDevice?) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = manager.adapter
        bluetoothAdapter.getProfileProxy(
            context,
            object : ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    Log.v(
                        TAG,
                        "internal_setActiveDevice result: " + internal_setActiveDevice(
                            proxy as BluetoothHeadset,
                            device
                        )
                    )
                    Log.v(
                        TAG, "internal_setPriority result: " + internal_setPriority(
                            proxy, device, 100
                        )
                    ) // BluetoothProfile.PRIORITY_ON
                    internal_setAudioRouteAllowed(proxy, true)
                    internal_setForceScoAudio(proxy, true)
                    Log.v(
                        TAG, "internal_connectAudio result: " + internal_connectAudio(
                            proxy
                        )
                    )
                    val audioManager =
                        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.isBluetoothScoOn = true
                    audioManager.startBluetoothSco()
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                }

                override fun onServiceDisconnected(profile: Int) {}
            },
            BluetoothProfile.HEADSET
        )
    }
}