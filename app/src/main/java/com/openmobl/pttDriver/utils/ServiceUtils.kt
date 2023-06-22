package com.openmobl.pttDriver.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build

object ServiceUtils {
    private val TAG = ServiceUtils::class.java.name

    //Credit (CC BY-SA 3.0): https://stackoverflow.com/a/5921190
    fun isServiceRunning(serviceClass: Class<*>?, context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass!!.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun startService(serviceClass: Class<*>?, context: Context) {
        if (!isServiceRunning(serviceClass, context)) {
            val driverService = Intent(context, serviceClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(driverService)
            } else {
                context.startService(driverService)
            }
        }
    }
}