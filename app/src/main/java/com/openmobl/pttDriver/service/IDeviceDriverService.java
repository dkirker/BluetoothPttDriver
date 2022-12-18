package com.openmobl.pttDriver.service;

import android.bluetooth.BluetoothDevice;

import com.openmobl.pttDriver.model.PttDriver;

public interface IDeviceDriverService {
    void setPttDevice(BluetoothDevice device);
    void setPttWatchForDevice(BluetoothDevice device);
    void setPttWatchForDevice(String name);

    void setPttDriver(PttDriver driver);

    void setConnectOnComplete(boolean connectOnComplete);
    boolean getConnectOnComplete();

    void setAutomaticallyReconnect(boolean autoReconnect);
    boolean getAutomaticallyReconnect();

    void setPttDownKeyDelay(int delay);
    int getPttDownKeyDelay();

    void registerStatusListener(DeviceDriverService.DeviceStatusListener statusListener);
    void unregisterStatusListener(DeviceDriverService.DeviceStatusListener statusListener);

    DeviceDriverService.Connected getConnected();

    void connect();
    void disconnect();
}
