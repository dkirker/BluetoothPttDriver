package com.openmobl.pttDriver.bt;

import java.util.UUID;

public interface SerialListener {
    void onSerialConnect();
    void onSerialConnect(UUID service, UUID characteristic);
    void onSerialConnectError(Exception e);
    void onSerialDisconnect();
    void onSerialRead(byte[] data, UUID service, UUID characteristic);
    void onSerialIoError(Exception e);
    void onBatteryEvent(byte level);
}
