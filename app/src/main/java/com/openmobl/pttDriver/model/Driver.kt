package com.openmobl.pttDriver.model

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import com.openmobl.pttDriver.model.Record.RecordType

class Driver : Record, Parcelable {
    override var id = 0
    override var name: String? = null
        private set
    var type: String? = null
        private set
    var json: String? = null
        private set
    var deviceNameMatch: String? = null
        private set
    var watchForDeviceName: String? = null
        private set

    constructor(name: String?, json: String?) {
        id = -1
        this.name = name
        type = "" // read from JSON
        this.json = json // validate JSON
        deviceNameMatch = null
        watchForDeviceName = null
    }

    constructor(id: Int, name: String?, json: String?) : this(name, json) {
        this.id = id
    }

    constructor(name: String?, type: String?, json: String?) : this(name, json) {
        this.type = type
    }

    constructor(id: Int, name: String?, type: String?, json: String?) : this(id, name, json) {
        this.type = type
    }

    constructor(
        name: String?,
        type: String?,
        json: String?,
        deviceNameMatch: String?,
        watchForDeviceName: String?
    ) : this(name, json) {
        this.type = type
        this.deviceNameMatch = deviceNameMatch
        this.watchForDeviceName = watchForDeviceName
    }

    constructor(
        id: Int,
        name: String?,
        type: String?,
        json: String?,
        deviceNameMatch: String?,
        watchForDeviceName: String?
    ) : this(id, name, json) {
        this.type = type
        this.deviceNameMatch = deviceNameMatch
        this.watchForDeviceName = watchForDeviceName
    }

    private constructor(parcel: Parcel) {
        readFromParcel(parcel)
    }

    override val details: String
        get() = ""
    override val recordType: RecordType
        get() = RecordType.DRIVER

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(name)
        parcel.writeString(type)
        parcel.writeString(json)
        parcel.writeString(deviceNameMatch)
        parcel.writeString(watchForDeviceName)
    }

    private fun readFromParcel(parcel: Parcel) {
        id = parcel.readInt()
        name = parcel.readString()
        type = parcel.readString()
        json = parcel.readString()
        deviceNameMatch = parcel.readString()
        watchForDeviceName = parcel.readString()
    }

    override fun toString(): String {
        return name!!
    }

    fun toStringFull(): String {
        return "Driver(" + id + ", " + name + ", " + type + ", JSON String)"
    }

    companion object {
        private val TAG = Driver::class.java.name
        @JvmField
        val CREATOR: Creator<Driver> = object : Creator<Driver> {
            override fun createFromParcel(parcel: Parcel): Driver {
                return Driver(parcel)
            }

            override fun newArray(i: Int): Array<Driver?> {
                return arrayOfNulls(i)
            }
        }
        val emptyDriver: Driver
            get() = Driver("", "")
    }
}