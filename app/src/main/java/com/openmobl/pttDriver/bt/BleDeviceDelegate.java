package com.openmobl.pttDriver.bt;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BleDeviceDelegate {
    private static final String TAG = BleDeviceDelegate.class.getName();

    /*
    private UUID mReadServiceUUID;
    private UUID mReadCharacteristicUUID;
    private BluetoothGattCharacteristic mReadCharacteristic;
    private UUID mWriteServiceUUID;
    private UUID mWriteCharacteristicUUID;
    private BluetoothGattCharacteristic mWriteCharacteristic;

    public BleDeviceDelegate() { }
    public BleDeviceDelegate(String readServiceUUID, String readCharacteristicUUID) {
        this(UUID.fromString(readServiceUUID), UUID.fromString(readCharacteristicUUID));
    }
    public BleDeviceDelegate(UUID readServiceUUID, UUID readCharacteristicUUID) {
        mReadServiceUUID = readServiceUUID;
        mReadCharacteristicUUID = readCharacteristicUUID;
    }
    public BleDeviceDelegate(String readServiceUUID, String readCharacteristicUUID,
                          String writeServiceUUID, String writeCharacteristicUUID) {
        this(UUID.fromString(readServiceUUID), UUID.fromString(readCharacteristicUUID),
                UUID.fromString(writeServiceUUID), UUID.fromString(writeCharacteristicUUID));
    }
    public BleDeviceDelegate(UUID readServiceUUID, UUID readCharacteristicUUID,
                          UUID writeServiceUUID, UUID writeCharacteristicUUID) {
        this(readServiceUUID, readCharacteristicUUID);
        mWriteServiceUUID = writeServiceUUID;
        mWriteCharacteristicUUID = writeCharacteristicUUID;
    }

    //public boolean connectCharacteristics(BluetoothGattService s) { return true; }

    public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {  }
    public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {  }
    public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {  }
    public boolean canWrite() { return true; }
    public void disconnect() {  }

    public UUID getReadServiceUUID() { return mReadServiceUUID; }
    public void setReadServiceUUID(UUID readServiceUUID) {
        mReadServiceUUID = readServiceUUID;
    }
    public void setReadServiceUUID(String readServiceUUID) {
        setReadServiceUUID(UUID.fromString(readServiceUUID));
    }
    public UUID getReadCharacteristicUUID() { return mReadCharacteristicUUID; }
    public void setReadCharacteristicUUID(UUID readCharacteristicUUID) {
        mReadCharacteristicUUID = readCharacteristicUUID;
    }
    public void setReadCharacteristicUUID(String readCharacteristicUUID) {
        setReadCharacteristicUUID(UUID.fromString(readCharacteristicUUID));
    }
    public BluetoothGattCharacteristic getReadCharacteristic() { return mReadCharacteristic; }
    public void setReadCharacteristic(BluetoothGattCharacteristic readCharacteristic) {
        mReadCharacteristic = readCharacteristic;
    }

    public UUID getWriteServiceUUID() { return mWriteServiceUUID; }
    public void setWriteServiceUUID(UUID writeServiceUUID) {
        mWriteServiceUUID = writeServiceUUID;
    }
    public void setWriteServiceUUID(String writeServiceUUID) {
        setWriteServiceUUID(UUID.fromString(writeServiceUUID));
    }
    public UUID getWriteCharacteristicUUID() { return mWriteCharacteristicUUID; }
    public void setWriteCharacteristicUUID(UUID writeCharacteristicUUID) {
        mWriteCharacteristicUUID = writeCharacteristicUUID;
    }
    public void setWriteCharacteristicUUID(String writeCharacteristicUUID) {
        setWriteCharacteristicUUID(UUID.fromString(writeCharacteristicUUID));
    }
    public BluetoothGattCharacteristic getWriteCharacteristic() { return mWriteCharacteristic; }
    public void setWriteCharacteristic(BluetoothGattCharacteristic writeCharacteristic) {
        mWriteCharacteristic = writeCharacteristic;
    }
    */


    private Map<UUID, Map<UUID, BluetoothGattCharacteristic>> mReadServices;
    private Map<UUID, Map<UUID, BluetoothGattCharacteristic>> mWriteServices;

    public BleDeviceDelegate() {
        mReadServices = new HashMap<>();
        mWriteServices = new HashMap<>();
    }

    public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {  }
    public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {  }
    public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {  }
    public boolean canWrite() {
        return !mWriteServices.isEmpty(); // Not a complete test...
    }
    public void disconnect() {  }

    public void addReadService(UUID readService) {
        if (!mReadServices.containsKey(readService)) {
            mReadServices.put(readService, new HashMap<>());
        }
    }
    public boolean containsReadService(UUID readService) {
        return mReadServices.containsKey(readService);
    }
    public List<UUID> getReadServices() {
        List<UUID> services = new ArrayList<>();

        for (Map.Entry<UUID, Map<UUID, BluetoothGattCharacteristic>> mapping : mReadServices.entrySet()) {
            services.add(mapping.getKey());
        }

        return services;
    }
    public void removeReadService(UUID readService) {
        if (mReadServices.containsKey(readService)) {
            mReadServices.remove(readService);
        }
    }
    public void addReadCharacteristic(UUID readService, UUID readCharacteristic) {
        addReadService(readService);
        if (mReadServices.containsKey(readService) &&
                !mReadServices.get(readService).containsKey(readCharacteristic)) {
            mReadServices.get(readService).put(readCharacteristic, null);
        }
    }
    public boolean containsReadCharacteristic(UUID readService, UUID readCharacteristic) {
        return mReadServices.containsKey(readService) &&
                mReadServices.get(readService).containsKey(readCharacteristic);
    }
    public List<UUID> getReadCharacteristics(UUID readService) {
        if (mReadServices.containsKey(readService)) {
            List<UUID> characteristics = new ArrayList<>();

            for (Map.Entry<UUID, BluetoothGattCharacteristic> mapping : mReadServices.get(readService).entrySet()) {
                characteristics.add(mapping.getKey());
            }

            return characteristics;
        }
        return null;
    }
    public void removeReadCharacteristic(UUID readService, UUID readCharacteristic) {
        if (mReadServices.containsKey(readService) &&
                mReadServices.get(readService).containsKey(readCharacteristic)) {
            mReadServices.get(readService).remove(readCharacteristic);
        }
    }
    public void setReadCharacteristic(UUID readService, UUID readCharacteristic, BluetoothGattCharacteristic gatt) {
        if (mReadServices.containsKey(readService) &&
                mReadServices.get(readService).containsKey(readCharacteristic)) {
            mReadServices.get(readService).put(readCharacteristic, gatt);
        }
    }
    public List<BluetoothGattCharacteristic> getAllReadCharacteristics() {
        List<BluetoothGattCharacteristic> characteristics = new ArrayList<>();

        for (Map.Entry<UUID, Map<UUID, BluetoothGattCharacteristic>> mapping : mReadServices.entrySet()) {
            for (Map.Entry<UUID, BluetoothGattCharacteristic> charMap : mapping.getValue().entrySet()) {
                characteristics.add(charMap.getValue());
            }
        }

        return characteristics;
    }
    public BluetoothGattCharacteristic getReadCharacteristic(UUID readService, UUID readCharacteristic) {
        if (mReadServices.containsKey(readService) &&
                mReadServices.get(readService).containsKey(readCharacteristic)) {
            return mReadServices.get(readService).get(readCharacteristic);
        }
        return null;
    }
    public BluetoothGattCharacteristic getReadCharacteristic(UUID readCharacteristic) {
        for (Map.Entry<UUID, Map<UUID, BluetoothGattCharacteristic>> mapping : mReadServices.entrySet()) {
            if (mapping.getValue().containsKey(readCharacteristic)) {
                return mapping.getValue().get(readCharacteristic);
            }
        }
        return null;
    }
    public boolean containsReadCharacteristic(UUID readCharacteristic) {
        for (Map.Entry<UUID, Map<UUID, BluetoothGattCharacteristic>> mapping : mReadServices.entrySet()) {
            if (mapping.getValue().containsKey(readCharacteristic)) {
                return true;
            }
        }
        return false;
    }

    public void addWriteService(UUID writeService) {
        if (!mWriteServices.containsKey(writeService)) {
            mWriteServices.put(writeService, new HashMap<>());
        }
    }
    public boolean containsWriteService(UUID writeService) {
        return mWriteServices.containsKey(writeService);
    }
    public List<UUID> getWriteServices() {
        List<UUID> services = new ArrayList<>();

        for (Map.Entry<UUID, Map<UUID, BluetoothGattCharacteristic>> mapping : mWriteServices.entrySet()) {
            services.add(mapping.getKey());
        }

        return services;
    }
    public void removeWriteService(UUID writeService) {
        if (mWriteServices.containsKey(writeService)) {
            mWriteServices.remove(writeService);
        }
    }
    public void addWriteCharacteristic(UUID writeService, UUID writeCharacteristic) {
        addWriteService(writeService);
        if (mWriteServices.containsKey(writeService) &&
                !mWriteServices.get(writeService).containsKey(writeCharacteristic)) {
            mWriteServices.get(writeService).put(writeCharacteristic, null);
        }
    }
    public boolean containsWriteCharacteristic(UUID writeService, UUID writeCharacteristic) {
        return mWriteServices.containsKey(writeService) &&
                mWriteServices.get(writeService).containsKey(writeCharacteristic);
    }
    public List<UUID> getWriteCharacteristics(UUID writeService) {
        if (mWriteServices.containsKey(writeService)) {
            List<UUID> characteristics = new ArrayList<>();

            for (Map.Entry<UUID, BluetoothGattCharacteristic> mapping : mWriteServices.get(writeService).entrySet()) {
                characteristics.add(mapping.getKey());
            }

            return characteristics;
        }
        return null;
    }
    public void removeWriteCharacteristic(UUID writeService, UUID writeCharacteristic) {
        if (mWriteServices.containsKey(writeService) &&
                mWriteServices.get(writeService).containsKey(writeCharacteristic)) {
            mWriteServices.get(writeService).remove(writeCharacteristic);
        }
    }
    public void setWriteCharacteristic(UUID writeService, UUID writeCharacteristic, BluetoothGattCharacteristic gatt) {
        if (mWriteServices.containsKey(writeService) &&
                mWriteServices.get(writeService).containsKey(writeCharacteristic)) {
            mWriteServices.get(writeService).put(writeCharacteristic, gatt);
        }
    }
    public List<BluetoothGattCharacteristic> getAllWriteCharacteristics() {
        List<BluetoothGattCharacteristic> characteristics = new ArrayList<>();

        for (Map.Entry<UUID, Map<UUID, BluetoothGattCharacteristic>> mapping : mWriteServices.entrySet()) {
            for (Map.Entry<UUID, BluetoothGattCharacteristic> charMap : mapping.getValue().entrySet()) {
                characteristics.add(charMap.getValue());
            }
        }

        return characteristics;
    }
    public BluetoothGattCharacteristic getWriteCharacteristic(UUID writeService, UUID writeCharacteristic) {
        if (mWriteServices.containsKey(writeService) &&
                mWriteServices.get(writeService).containsKey(writeCharacteristic)) {
            return mWriteServices.get(writeService).get(writeCharacteristic);
        }
        return null;
    }
    public BluetoothGattCharacteristic getWriteCharacteristic(UUID writeCharacteristic) {
        for (Map.Entry<UUID, Map<UUID, BluetoothGattCharacteristic>> mapping : mWriteServices.entrySet()) {
            if (mapping.getValue().containsKey(writeCharacteristic)) {
                return mapping.getValue().get(writeCharacteristic);
            }
        }
        return null;
    }
    public boolean containsWriteCharacteristic(UUID writeCharacteristic) {
        for (Map.Entry<UUID, Map<UUID, BluetoothGattCharacteristic>> mapping : mWriteServices.entrySet()) {
            if (mapping.getValue().containsKey(writeCharacteristic)) {
                return true;
            }
        }
        return false;
    }
}