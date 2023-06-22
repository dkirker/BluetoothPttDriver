package com.openmobl.pttDriver.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.openmobl.pttDriver.model.Device.DeviceType
import com.openmobl.pttDriver.model.PttDriver.ConnectionType
import java.util.*

class DeviceDriverServiceManager {
    class DeviceDriverServiceHolder(listener: DeviceStatusListener?) {
        var service: IDeviceDriverService? = null
        val connection: ServiceConnection

        init {
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    Log.v(TAG, "onServiceConnected")
                    this.service = (service as DeviceDriverServiceBinder).service
                    service.registerStatusListener(listener)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    Log.v(TAG, "onServiceDisconnected")
                    service = null
                }
            }
        }
    }

    private val mServiceMap: MutableMap<Int, DeviceDriverServiceHolder>

    init {
        mServiceMap = HashMap()
    }

    fun createService(deviceId: Int, listener: DeviceStatusListener?) {
        mServiceMap[deviceId] = DeviceDriverServiceHolder(listener)
    }

    fun recreateService(deviceId: Int, listener: DeviceStatusListener?) {
        val oldService = getService(deviceId)
        if (oldService == null) {
            createService(deviceId, listener)
        }
    }

    fun getServiceHolder(deviceId: Int): DeviceDriverServiceHolder? {
        return mServiceMap[deviceId]
    }

    val allServices: Map<Int, DeviceDriverServiceHolder>
        get() = Collections.unmodifiableMap(mServiceMap)

    fun getService(deviceId: Int): IDeviceDriverService? {
        val holder = getServiceHolder(deviceId)
        return holder?.service
    }

    fun getConnection(deviceId: Int): ServiceConnection? {
        val holder = getServiceHolder(deviceId)
        return holder?.connection
    }

    companion object {
        private val TAG = DeviceDriverServiceManager::class.java.name
        fun getServiceClassForDeviceType(
            deviceType: DeviceType?,
            driverConnection: ConnectionType?
        ): Class<*>? {
            if (deviceType == DeviceType.BLUETOOTH) {
                return BluetoothDeviceDriverService::class.java
            } else if (deviceType == DeviceType.LOCAL) {
                if (driverConnection == ConnectionType.FILESTREAM) {
                    return FileStreamDeviceDriverService::class.java
                }
            }
            return null // ??
        }
    }
}