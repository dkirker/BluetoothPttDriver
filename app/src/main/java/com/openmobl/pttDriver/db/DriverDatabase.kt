package com.openmobl.pttDriver.db

import com.openmobl.pttDriver.model.Device
import com.openmobl.pttDriver.model.Driver

interface DriverDatabase {
    fun open()
    fun close()
    val devices: List<Device>
    val drivers: List<Driver>
    fun getDevice(id: Int): Device?
    fun getDevice(macAddress: String): Device?
    fun deviceExists(id: Int): Boolean
    fun deviceExists(macAddress: String?): Boolean
    fun addDevice(device: Device)
    fun addOrUpdateDevice(device: Device)
    fun updateDevice(device: Device)
    fun removeDevice(id: Int)
    fun removeDevice(device: Device?)
    fun getDriver(id: Int): Driver?
    fun getDriver(name: String): Driver?
    fun driverExists(id: Int): Boolean
    fun driverExists(name: String?): Boolean
    fun addDriver(driver: Driver)
    fun addOrUpdateDriver(driver: Driver)
    fun updateDriver(driver: Driver)
    fun removeDriver(id: Int)
    fun removeDriver(driver: Driver?)
}