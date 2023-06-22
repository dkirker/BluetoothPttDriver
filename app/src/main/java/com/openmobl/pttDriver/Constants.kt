package com.openmobl.pttDriver

object Constants {
    // values have to be globally unique
    const val INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect"
    const val INTENT_ACTION_RECONNECT = BuildConfig.APPLICATION_ID + ".Reconnect"
    const val INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".app.MainActivity"

    // values have to be unique within each app
    const val NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001
}