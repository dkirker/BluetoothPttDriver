package com.openmobl.pttDriver.model

import com.openmobl.pttDriver.model.DeviceWithStatus

class DeviceWithStatus : Device {
    var connected = false
    var batteryLevel = 0

    constructor(
        type: DeviceType?, name: String?, macAddress: String?,
        autoConnect: Boolean, autoReconnect: Boolean, pttDownDelay: Int
    ) : super(type, name, macAddress, autoConnect, autoReconnect, pttDownDelay) {
        initialize()
    }

    constructor(
        id: Int, type: DeviceType?, name: String?, macAddress: String?,
        autoConnect: Boolean, autoReconnect: Boolean, pttDownDelay: Int
    ) : super(id, type, name, macAddress, autoConnect, autoReconnect, pttDownDelay) {
        initialize()
    }

    constructor(
        type: DeviceType?, name: String?, macAddress: String?,
        driverId: Int, autoConnect: Boolean, autoReconnect: Boolean, pttDownDelay: Int
    ) : super(type, name, macAddress, driverId, autoConnect, autoReconnect, pttDownDelay) {
        initialize()
    }

    constructor(
        id: Int, type: DeviceType?, name: String?, macAddress: String?,
        driverId: Int, autoConnect: Boolean, autoReconnect: Boolean, pttDownDelay: Int
    ) : super(id, type, name, macAddress, driverId, autoConnect, autoReconnect, pttDownDelay) {
        initialize()
    }

    constructor(
        id: Int,
        type: DeviceType?,
        name: String?,
        macAddress: String?,
        driverId: Int,
        driverName: String?,
        autoConnect: Boolean,
        autoReconnect: Boolean,
        pttDownDelay: Int
    ) : super(
        id,
        type,
        name,
        macAddress,
        driverId,
        driverName,
        autoConnect,
        autoReconnect,
        pttDownDelay
    ) {
        initialize()
    }

    constructor(device: Device) : super(
        device.id, device.deviceType, device.name, device.macAddress,
        device.driverId, device.driverName,
        device.autoConnect, device.autoReconnect, device.pttDownDelay
    ) {
        initialize()
    }

    private fun initialize() {
        connected = false
        batteryLevel = 0
    }

    companion object {
        private val TAG = DeviceWithStatus::class.java.name
    }
}