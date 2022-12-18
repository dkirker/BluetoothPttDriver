package com.openmobl.pttDriver.db;

import com.openmobl.pttDriver.model.Device;
import com.openmobl.pttDriver.model.Driver;

import java.util.List;

public interface DriverDatabase {
    void open();
    void close();

    List<Device> getDevices();
    List<Driver> getDrivers();

    Device getDevice(int id);
    Device getDevice(String macAddress);
    boolean deviceExists(int id);
    boolean deviceExists(String macAddress);
    void addDevice(Device device);
    void addOrUpdateDevice(Device device);
    void updateDevice(Device device);
    void removeDevice(int id);
    void removeDevice(Device device);

    Driver getDriver(int id);
    Driver getDriver(String name);
    boolean driverExists(int id);
    boolean driverExists(String name);
    void addDriver(Driver driver);
    void addOrUpdateDriver(Driver driver);
    void updateDriver(Driver driver);
    void removeDriver(int id);
    void removeDriver(Driver driver);
}
