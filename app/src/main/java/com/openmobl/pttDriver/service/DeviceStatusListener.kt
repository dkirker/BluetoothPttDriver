package com.openmobl.pttDriver.service

interface DeviceStatusListener {
    fun onStatusMessageUpdate(message: String?)
    fun onConnected()
    fun onDisconnected()
    fun onBatteryEvent(level: Byte)
}