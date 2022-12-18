package com.openmobl.pttDriver.app.ui.main;

import android.app.Application;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.openmobl.pttDriver.app.MainActivity;
import com.openmobl.pttDriver.db.DriverDatabase;
import com.openmobl.pttDriver.model.ModelDataAction;
import com.openmobl.pttDriver.model.Record;

import java.util.List;

public class DeviceOrDriverViewModel extends ViewModel {
    private static final String TAG = DeviceOrDriverViewModel.class.getName();

    public static final String ARG_DATAEVENT_RECORD = "dataevent_record";
    public static final String ARG_DATAEVENT_ACTION = "dataevent_action";

    private MutableLiveData<Bundle> mModelDataEvent = new MutableLiveData<>();

    public void setDataSource(DriverDatabase db) { }

    public void sendDataEvent(Record record, ModelDataAction action) {
        Bundle newEvent = new Bundle();

        newEvent.putString(ARG_DATAEVENT_ACTION, action.toString());
        // Can be dangerous, but we know Record is either a Driver or Device, and those are Parcelable.
        // We should enforce this instead of assume it.
        newEvent.putParcelable(ARG_DATAEVENT_RECORD, (Parcelable)record);

        mModelDataEvent.setValue(newEvent);
    }
    public LiveData<Bundle> getDataEvent() {
        return mModelDataEvent;
    }

    public void refreshSource() { }
    public LiveData<List<Record>> getRecords() { return null; }
}
