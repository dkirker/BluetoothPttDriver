package com.openmobl.pttDriver.service;

import android.content.Context;
import android.content.Intent;

import java.util.HashMap;
import java.util.Map;

public class DeviceEventBroadcaster {
    private static final String TAG = DeviceEventBroadcaster.class.getName();

    public static final String INTENT_DEVICE_CONNECTED = "com.openmobl.pttDriver.device.CONNECTED";
    public static final String INTENT_DEVICE_DISCONNECTED = "com.openmobl.pttDriver.device.DISCONNECTED";
    public static final String INTENT_DEVICE_BATTERY = "com.openmobl.pttDriver.device.BATTERY";

    public static final String EXTRA_DEVICE_NAME = "deviceName";
    public static final String EXTRA_DEVICE_ADDRESS = "deviceAddress";
    public static final String EXTRA_BATTERY_VALUE = "batteryValue";
    public static final String EXTRA_BATTERY_UNIT = "batteryUnit";

    private DeviceEventBroadcaster() {
    }

    private static void sendIntent(Context context, String intentName, Map<String, String> extras) {
        Intent intent = new Intent();

        intent.setAction(intentName);
        if (extras != null) {
            for (Map.Entry<String, String> extra: extras.entrySet()) {
                intent.putExtra(extra.getKey(), extra.getValue());
            }
        }

        context.sendBroadcast(intent);
    }

    public static void sendDeviceConnectionState(Context context, boolean connected, String deviceName, String deviceAddress) {
        HashMap<String, String> extras = new HashMap<>();

        extras.put(EXTRA_DEVICE_NAME, deviceName);
        extras.put(EXTRA_DEVICE_ADDRESS, deviceAddress);

        sendIntent(context, connected ? INTENT_DEVICE_CONNECTED : INTENT_DEVICE_DISCONNECTED, extras);
    }

    public static void sendDeviceConnected(Context context, String deviceName, String deviceAddress) {
        sendDeviceConnectionState(context, true, deviceName, deviceAddress);
    }

    public static void sendDeviceDisconnected(Context context, String deviceName, String deviceAddress) {
        sendDeviceConnectionState(context, false, deviceName, deviceAddress);
    }

    public static void sendDeviceBatteryState(Context context, String deviceName, String deviceAddress, float percentage) {
        Intent intent = new Intent();

        intent.setAction(INTENT_DEVICE_BATTERY);
        intent.putExtra(EXTRA_DEVICE_NAME, deviceName);
        intent.putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress);
        intent.putExtra(EXTRA_BATTERY_VALUE, percentage);
        intent.putExtra(EXTRA_BATTERY_UNIT, "%");

        context.sendBroadcast(intent);
    }
}
