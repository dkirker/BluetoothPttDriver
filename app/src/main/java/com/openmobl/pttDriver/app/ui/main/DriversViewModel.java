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
import com.openmobl.pttDriver.model.Driver;
import com.openmobl.pttDriver.model.Record;

import java.util.ArrayList;
import java.util.List;

public class DriversViewModel extends DeviceOrDriverViewModel {
    private MutableLiveData<List<Driver>> mDrivers = new MutableLiveData<>();
    private DriverDatabase mDb;
    private LiveData<List<Record>> mRecords = Transformations.map(mDrivers, new Function<List<Driver>, List<Record>>() {
        @Override
        public List<Record> apply(List<Driver> input) {
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
            mDrivers.setValue(mDb.getDrivers());
    }

    @Override
    public LiveData<List<Record>> getRecords() {
        return mRecords;
    }
}