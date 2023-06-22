package com.openmobl.pttDriver.model

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import com.openmobl.pttDriver.model.Record.RecordType

open class Device : Record, Parcelable {
    enum class DeviceType(private val mType: String) {
        INVALID("-"), BLUETOOTH("bluetooth"), LOCAL("local");

        val isValid: Boolean
            get() = this != INVALID

        override fun toString(): String {
            return mType
        }

        companion object {
            fun toDeviceType(value: String?): DeviceType {
                for (typeEnum in values()) {
                    if (value == typeEnum.toString()) return typeEnum
                }
                return INVALID
            }
        }
    }

    override var id = 0
    var deviceType: DeviceType? = null
        private set
    override var name: String? = null
        private set
    var macAddress: String? = null
        private set
    var driverName: String? = null
        private set
    var driverId = 0
        private set
    var autoConnect = false
        private set
    var autoReconnect = false
        private set
    var pttDownDelay = 0
        private set

    constructor(
        type: DeviceType?, name: String?, macAddress: String?,
        autoConnect: Boolean, autoReconnect: Boolean, pttDownDelay: Int
    ) {
        id = -1
        deviceType = type
        this.name = name
        this.macAddress = macAddress
        driverId = -1
        driverName = ""
        this.autoConnect = autoConnect
        this.autoReconnect = autoReconnect
        this.pttDownDelay = pttDownDelay
    }

    constructor(
        id: Int, type: DeviceType?, name: String?, macAddress: String?,
        autoConnect: Boolean, autoReconnect: Boolean, pttDownDelay: Int
    ) : this(type, name, macAddress, autoConnect, autoReconnect, pttDownDelay) {
        this.id = id
    }

    constructor(
        type: DeviceType?, name: String?, macAddress: String?,
        driverId: Int, autoConnect: Boolean, autoReconnect: Boolean, pttDownDelay: Int
    ) : this(type, name, macAddress, autoConnect, autoReconnect, pttDownDelay) {
        this.driverId = driverId
    }

    constructor(
        id: Int, type: DeviceType?, name: String?, macAddress: String?,
        driverId: Int, autoConnect: Boolean, autoReconnect: Boolean, pttDownDelay: Int
    ) : this(id, type, name, macAddress, autoConnect, autoReconnect, pttDownDelay) {
        this.driverId = driverId
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
    ) : this(id, type, name, macAddress, driverId, autoConnect, autoReconnect, pttDownDelay) {
        this.driverName = driverName
    }

    private constructor(parcel: Parcel) {
        readFromParcel(parcel)
    }

    override val details: String
        get() = ""
    override val recordType: RecordType
        get() = RecordType.DEVICE

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(deviceType.toString())
        parcel.writeString(name)
        parcel.writeString(macAddress)
        parcel.writeInt(driverId)
        parcel.writeString(driverName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parcel.writeBoolean(autoConnect)
            parcel.writeBoolean(autoReconnect)
        } else {
            parcel.writeInt(if (autoConnect) 1 else 0)
            parcel.writeInt(if (autoReconnect) 1 else 0)
        }
        parcel.writeInt(pttDownDelay)
    }

    private fun readFromParcel(parcel: Parcel) {
        id = parcel.readInt()
        deviceType = DeviceType.toDeviceType(parcel.readString())
        name = parcel.readString()
        macAddress = parcel.readString()
        driverId = parcel.readInt()
        driverName = parcel.readString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            autoConnect = parcel.readBoolean()
            autoReconnect = parcel.readBoolean()
        } else {
            autoConnect = parcel.readInt() != 0
            autoReconnect = parcel.readInt() != 0
        }
        pttDownDelay = parcel.readInt()
    }

    override fun toString(): String {
        return name + " (" + macAddress + ")"
    }

    fun toStringFull(): String {
        return "Device(" + id + ", " + deviceType + ", " + name + ", " + macAddress +
                ", " + driverId + ", " + driverName + ", " +
                autoConnect + ", " + autoReconnect + ", " + pttDownDelay + ")"
    }

    companion object {
        private val TAG = Device::class.java.name
        val CREATOR: Creator<Device> = object : Creator<Device?> {
            override fun createFromParcel(parcel: Parcel): Device? {
                return Device(parcel)
            }

            override fun newArray(i: Int): Array<Device?> {
                return arrayOfNulls(i)
            }
        }
        val autoConnectDefault: Boolean
            get() = true
        val autoReconnectDefault: Boolean
            get() = true
        val pttDownDelayDefault: Int
            get() = 0
    }
}