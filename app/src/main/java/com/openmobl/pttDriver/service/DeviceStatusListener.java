package com.openmobl.pttDriver.service;

public interface DeviceStatusListener {
    void onStatusMessageUpdate(String message);

    void onConnected();
    void onDisconnected();

    void onBatteryEvent(byte level);
}
