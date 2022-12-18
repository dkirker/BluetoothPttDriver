package com.openmobl.pttDriver.utils;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import java.lang.reflect.Method;

public class BluetoothScoAudioUtils {
    private static final String TAG = BluetoothScoAudioUtils.class.getName();

    private BluetoothScoAudioUtils() {}

    public static boolean internal_setActiveDevice(BluetoothHeadset headsetProxy, BluetoothDevice device) {
        try {
            Class[] argTypes = { BluetoothDevice.class };

            Method m = headsetProxy.getClass().getMethod("setActiveDevice", argTypes);
            boolean result = (boolean) m.invoke(headsetProxy, device);

            Log.v(TAG, "internal_setActiveDevice " + result);

            return result;
        } catch (Exception e) {
            Log.d(TAG, "internal_setActiveDevice threw exception " + e);

            return false;
        }
    }
    public static boolean internal_setPriority(BluetoothHeadset headsetProxy, BluetoothDevice device, int priority) {
        try {
            Class[] argTypes = { BluetoothDevice.class, int.class };

            Method m = headsetProxy.getClass().getMethod("setPriority", argTypes);
            boolean result = (boolean) m.invoke(headsetProxy, device, priority);

            Log.v(TAG, "internal_setPriority " + result);

            return result;
        } catch (Exception e) {
            Log.d(TAG, "internal_setPriority threw exception " + e);

            return false;
        }
    }
    public static boolean internal_connectAudio(BluetoothHeadset headsetProxy) {
        try {
            Class[] argTypes = {  };

            Method m = headsetProxy.getClass().getMethod("connectAudio", (Class[]) null);
            boolean result = (boolean) m.invoke(headsetProxy);

            Log.v(TAG, "internal_connectAudio " + result);

            return result;
        } catch (Exception e) {
            Log.d(TAG, "internal_connectAudio threw exception " + e);

            return false;
        }
    }
    public static void internal_setForceScoAudio(BluetoothHeadset headsetProxy, boolean forced) {
        try {
            Class[] argTypes = { boolean.class };

            Method m = headsetProxy.getClass().getMethod("setForceScoAudio", argTypes);
            m.invoke(headsetProxy, forced);

            Log.v(TAG, "internal_setForceScoAudio");
        } catch (Exception e) {
            Log.d(TAG, "internal_setForceScoAudio threw exception " + e);
        }
    }
    public static void internal_setAudioRouteAllowed(BluetoothHeadset headsetProxy, boolean allowed) {
        try {
            Class[] argTypes = { boolean.class };

            Method m = headsetProxy.getClass().getMethod("setAudioRouteAllowed", argTypes);
            m.invoke(headsetProxy, allowed);

            Log.v(TAG, "internal_setAudioRouteAllowed");
        } catch (Exception e) {
            Log.d(TAG, "internal_setAudioRouteAllowed threw exception " + e);
        }
    }

    public static void giveDevicePriority(Context context, BluetoothDevice device) {
        BluetoothManager manager = (BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = manager.getAdapter();
        bluetoothAdapter.getProfileProxy(context,
                new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        Log.v(TAG, "internal_setActiveDevice result: " + internal_setActiveDevice((BluetoothHeadset)proxy, device));
                        Log.v(TAG, "internal_setPriority result: " + internal_setPriority((BluetoothHeadset)proxy, device, 100)); // BluetoothProfile.PRIORITY_ON
                        internal_setAudioRouteAllowed((BluetoothHeadset)proxy, true);
                        internal_setForceScoAudio((BluetoothHeadset)proxy, true);
                        Log.v(TAG, "internal_connectAudio result: " + internal_connectAudio((BluetoothHeadset)proxy));


                        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                        audioManager.setBluetoothScoOn(true);
                        audioManager.startBluetoothSco();

                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy);
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {

                    }
                },
                BluetoothProfile.HEADSET);
    }
}
