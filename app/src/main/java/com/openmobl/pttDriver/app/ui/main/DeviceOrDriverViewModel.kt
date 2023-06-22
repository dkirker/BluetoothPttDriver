package com.openmobl.pttDriver.app.ui.main

import android.os.Bundle
import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.openmobl.pttDriver.db.DriverDatabase
import com.openmobl.pttDriver.model.ModelDataAction
import com.openmobl.pttDriver.model.Record

open class DeviceOrDriverViewModel : ViewModel() {
    private val mModelDataEvent = MutableLiveData<Bundle>()
    open fun setDataSource(db: DriverDatabase?) {}
    fun sendDataEvent(record: Record?, action: ModelDataAction) {
        val newEvent = Bundle()
        newEvent.putString(ARG_DATAEVENT_ACTION, action.toString())
        // Can be dangerous, but we know Record is either a Driver or Device, and those are Parcelable.
        // We should enforce this instead of assume it.
        newEvent.putParcelable(ARG_DATAEVENT_RECORD, record as Parcelable?)
        mModelDataEvent.value = newEvent
    }

    val dataEvent: LiveData<Bundle>
        get() = mModelDataEvent

    open fun refreshSource() {}
    open val records: LiveData<List<Record>>?
        get() = null

    companion object {
        private val TAG = DeviceOrDriverViewModel::class.java.name
        const val ARG_DATAEVENT_RECORD = "dataevent_record"
        const val ARG_DATAEVENT_ACTION = "dataevent_action"
    }
}