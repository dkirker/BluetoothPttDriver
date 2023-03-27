package com.openmobl.pttDriver.service;

import android.os.Binder;

public class DeviceDriverServiceBinder extends Binder {
    private final IDeviceDriverService mService;

    protected DeviceDriverServiceBinder(IDeviceDriverService service) {
        mService = service;
    }

    public IDeviceDriverService getService() {
        return mService;
    }
}