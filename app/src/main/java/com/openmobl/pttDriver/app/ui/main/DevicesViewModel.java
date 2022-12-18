package com.openmobl.pttDriver.app.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.openmobl.pttDriver.db.DriverDatabase;
import com.openmobl.pttDriver.db.DriverSQLiteDatabase;
import com.openmobl.pttDriver.model.Device;
import com.openmobl.pttDriver.model.DeviceWithStatus;
import com.openmobl.pttDriver.model.Record;

import java.util.ArrayList;
import java.util.List;

public class DevicesViewModel extends DeviceOrDriverViewModel {
    private MutableLiveData<List<Device>> mDevices = new MutableLiveData<>();
    private DriverDatabase mDb;
    private LiveData<List<Record>> mRecords = Transformations.map(mDevices, new Function<List<Device>, List<Record>>() {
        @Override
        public List<Record> apply(List<Device> input) {
            List<Record> records = new ArrayList<>();

            records.addAll(input);

            return records;
        }
    });

    @Override
    public void setDataSource(DriverDatabase db) {
        mDb = db;
    }

    @Override
    public void refreshSource() {
        if (mDb != null)
            mDevices.setValue(mDb.getDevices());
    }

    @Override
    public LiveData<List<Record>> getRecords() {
        return mRecords;
    }

    //public LiveData<DeviceWithStatus>
}