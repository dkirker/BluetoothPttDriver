package com.openmobl.pttDriver.model;

public class DeviceWithStatus extends Device {
    private static final String TAG = DeviceWithStatus.class.getName();

    private boolean mConnected;
    private int mBatteryLevel;

    public DeviceWithStatus(DeviceType type, String name, String macAddress,
                  boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        super(type, name, macAddress, autoConnect, autoReconnect, pttDownDelay);

        initialize();
    }

    public DeviceWithStatus(int id, DeviceType type, String name, String macAddress,
                  boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        super(id, type, name, macAddress, autoConnect, autoReconnect, pttDownDelay);

        initialize();
    }
    public DeviceWithStatus(DeviceType type, String name, String macAddress,
                  int driverId, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        super(type, name, macAddress, driverId, autoConnect, autoReconnect, pttDownDelay);

        initialize();
    }
    public DeviceWithStatus(int id, DeviceType type, String name, String macAddress,
                  int driverId, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        super(id, type, name, macAddress, driverId, autoConnect, autoReconnect, pttDownDelay);

        initialize();
    }
    public DeviceWithStatus(int id, DeviceType type, String name, String macAddress,
                  int driverId, String driverName, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        super(id, type, name, macAddress, driverId, driverName, autoConnect, autoReconnect, pttDownDelay);

        initialize();
    }

    public DeviceWithStatus(Device device) {
        super(device.getId(), device.getDeviceType(), device.getName(), device.getMacAddress(),
                device.getDriverId(), device.getDriverName(),
                device.getAutoConnect(), device.getAutoReconnect(), device.getPttDownDelay());

        initialize();
    }

    private void initialize() {
        mConnected = false;
        mBatteryLevel = 0;
    }

    public void setBatteryLevel(int level) {
        mBatteryLevel = level;
    }
    public int getBatteryLevel() {
        return mBatteryLevel;
    }
    public void setConnected(boolean connected) {
        mConnected = connected;
    }
    public boolean getConnected() {
        return mConnected;
    }
}
