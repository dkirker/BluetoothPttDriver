package com.openmobl.pttDriver.service

import android.content.Context
import android.content.Intent

object DeviceEventBroadcaster {
    private val TAG = DeviceEventBroadcaster::class.java.name
    const val INTENT_DEVICE_CONNECTED = "com.openmobl.pttDriver.device.CONNECTED"
    const val INTENT_DEVICE_DISCONNECTED = "com.openmobl.pttDriver.device.DISCONNECTED"
    const val INTENT_DEVICE_BATTERY = "com.openmobl.pttDriver.device.BATTERY"
    const val EXTRA_DEVICE_NAME = "deviceName"
    const val EXTRA_DEVICE_ADDRESS = "deviceAddress"
    const val EXTRA_BATTERY_VALUE = "batteryValue"
    const val EXTRA_BATTERY_UNIT = "batteryUnit"
    private fun sendIntent(context: Context, intentName: String, extras: Map<String, String?>?) {
        val intent = Intent()
        intent.action = intentName
        if (extras != null) {
            for ((key, value) in extras) {
                intent.putExtra(key, value)
            }
        }
        context.sendBroadcast(intent)
    }

    fun sendDeviceConnectionState(
        context: Context,
        connected: Boolean,
        deviceName: String?,
        deviceAddress: String?
    ) {
        val extras = HashMap<String, String?>()
        extras[EXTRA_DEVICE_NAME] = deviceName
        extras[EXTRA_DEVICE_ADDRESS] = deviceAddress
        sendIntent(
            context,
            if (connected) INTENT_DEVICE_CONNECTED else INTENT_DEVICE_DISCONNECTED,
            extras
        )
    }

    fun sendDeviceConnected(context: Context, deviceName: String?, deviceAddress: String?) {
        sendDeviceConnectionState(context, true, deviceName, deviceAddress)
    }

    fun sendDeviceDisconnected(context: Context, deviceName: String?, deviceAddress: String?) {
        sendDeviceConnectionState(context, false, deviceName, deviceAddress)
    }

    fun sendDeviceBatteryState(
        context: Context,
        deviceName: String?,
        deviceAddress: String?,
        percentage: Float
    ) {
        val intent = Intent()
        intent.action = INTENT_DEVICE_BATTERY
        intent.putExtra(EXTRA_DEVICE_NAME, deviceName)
        intent.putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
        intent.putExtra(EXTRA_BATTERY_VALUE, percentage)
        intent.putExtra(EXTRA_BATTERY_UNIT, "%")
        context.sendBroadcast(intent)
    }
}