package com.openmobl.pttDriver.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.openmobl.pttDriver.service.BluetoothDeviceDriverService;

public class ServiceUtils {
    private static final String TAG = ServiceUtils.class.getName();

    //Credit (CC BY-SA 3.0): https://stackoverflow.com/a/5921190
    public static boolean isServiceRunning(Class<?> serviceClass, Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static void startService(Class<?> serviceClass, Context context) {
        if (!isServiceRunning(serviceClass, context)) {
            Intent driverService = new Intent(context, serviceClass);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(driverService);
            } else {
                context.startService(driverService);
            }
        }
    }
}
