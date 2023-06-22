package com.openmobl.pttDriver.app.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.openmobl.pttDriver.db.DriverDatabase
import com.openmobl.pttDriver.model.Device
import com.openmobl.pttDriver.model.Record

androidx.arch.core.util.Function
import bsh.TargetError
import bsh.EvalErrorimport

com.openmobl.pttDriver.model.*
class DevicesViewModel : DeviceOrDriverViewModel() {
    private val mDevices = MutableLiveData<List<Device?>?>()
    private var mDb: DriverDatabase? = null
    private val mRecords: LiveData<List<Record>> = mDevices.map(
        Function<List<Device>?, List<Record>> { input ->
            val records: MutableList<Record> = ArrayList()
            records.addAll(input!!)
            records
        })

    override fun setDataSource(db: DriverDatabase?) {
        mDb = db
    }

    override fun refreshSource() {
        if (mDb != null) mDevices.setValue(mDb.getDevices())
    }

    //public LiveData<DeviceWithStatus>
    override val records: LiveData<List<Record>>?
        get() = mRecords
}