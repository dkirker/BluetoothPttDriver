package com.openmobl.pttDriver.service;

import android.bluetooth.BluetoothDevice;

import com.openmobl.pttDriver.model.Device;
import com.openmobl.pttDriver.model.PttDriver;

public interface IDeviceDriverService {
    void setPttDevice(Device device);
    boolean deviceIsValid();

    void setPttDriver(PttDriver driver);

    void setAutomaticallyReconnect(boolean autoReconnect);
    boolean getAutomaticallyReconnect();

    void registerStatusListener(DeviceStatusListener statusListener);
    void unregisterStatusListener(DeviceStatusListener statusListener);

    DeviceConnectionState getConnectionState();

    void connect();
    void disconnect();
}
