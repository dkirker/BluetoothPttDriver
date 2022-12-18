package com.openmobl.pttDriver.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.openmobl.pttDriver.service.DeviceDriverService;

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

    public static void startService(Context context) {
        if (!isServiceRunning(DeviceDriverService.class, context)) {
            Intent driverService = new Intent(context, DeviceDriverService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(driverService);
            } else {
                context.startService(driverService);
            }
        }
    }
}
