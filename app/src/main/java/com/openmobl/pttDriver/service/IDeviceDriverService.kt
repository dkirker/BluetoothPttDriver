package com.openmobl.pttDriver.service

import com.openmobl.pttDriver.model.Device
import com.openmobl.pttDriver.model.PttDriver

interface IDeviceDriverService {
    fun setPttDevice(device: Device?)
    fun deviceIsValid(): Boolean
    fun setPttDriver(driver: PttDriver?)
    var automaticallyReconnect: Boolean
    fun registerStatusListener(statusListener: DeviceStatusListener?)
    fun unregisterStatusListener(statusListener: DeviceStatusListener?)
    val connectionState: DeviceConnectionState
    fun connect()
    fun disconnect()
}