package com.openmobl.pttDriver.bt

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import java.util.*

class BleDeviceDelegate {
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
    private val mReadServices: MutableMap<UUID?, MutableMap<UUID?, BluetoothGattCharacteristic?>>
    private val mWriteServices: MutableMap<UUID?, MutableMap<UUID?, BluetoothGattCharacteristic?>>

    init {
        mReadServices = HashMap()
        mWriteServices = HashMap()
    }

    fun onDescriptorWrite(g: BluetoothGatt?, d: BluetoothGattDescriptor?, status: Int) {}
    fun onCharacteristicChanged(g: BluetoothGatt?, c: BluetoothGattCharacteristic?) {}
    fun onCharacteristicWrite(g: BluetoothGatt?, c: BluetoothGattCharacteristic?, status: Int) {}
    fun canWrite(): Boolean {
        return !mWriteServices.isEmpty() // Not a complete test...
    }

    fun disconnect() {}
    fun addReadService(readService: UUID?) {
        if (!mReadServices.containsKey(readService)) {
            mReadServices[readService] = HashMap()
        }
    }

    fun containsReadService(readService: UUID?): Boolean {
        return mReadServices.containsKey(readService)
    }

    val readServices: List<UUID?>
        get() {
            val services: MutableList<UUID?> = ArrayList()
            for ((key) in mReadServices) {
                services.add(key)
            }
            return services
        }

    fun removeReadService(readService: UUID?) {
        if (mReadServices.containsKey(readService)) {
            mReadServices.remove(readService)
        }
    }

    fun addReadCharacteristic(readService: UUID?, readCharacteristic: UUID?) {
        addReadService(readService)
        if (mReadServices.containsKey(readService) &&
            !mReadServices[readService]!!.containsKey(readCharacteristic)
        ) {
            mReadServices[readService]!![readCharacteristic] = null
        }
    }

    fun containsReadCharacteristic(readService: UUID?, readCharacteristic: UUID?): Boolean {
        return mReadServices.containsKey(readService) &&
                mReadServices[readService]!!.containsKey(readCharacteristic)
    }

    fun getReadCharacteristics(readService: UUID?): List<UUID?>? {
        if (mReadServices.containsKey(readService)) {
            val characteristics: MutableList<UUID?> = ArrayList()
            for ((key) in mReadServices[readService]!!) {
                characteristics.add(key)
            }
            return characteristics
        }
        return null
    }

    fun removeReadCharacteristic(readService: UUID?, readCharacteristic: UUID?) {
        if (mReadServices.containsKey(readService) &&
            mReadServices[readService]!!.containsKey(readCharacteristic)
        ) {
            mReadServices[readService]!!.remove(readCharacteristic)
        }
    }

    fun setReadCharacteristic(
        readService: UUID?,
        readCharacteristic: UUID?,
        gatt: BluetoothGattCharacteristic?
    ) {
        if (mReadServices.containsKey(readService) &&
            mReadServices[readService]!!.containsKey(readCharacteristic)
        ) {
            mReadServices[readService]!![readCharacteristic] = gatt
        }
    }

    val allReadCharacteristics: List<BluetoothGattCharacteristic?>
        get() {
            val characteristics: MutableList<BluetoothGattCharacteristic?> = ArrayList()
            for ((_, value) in mReadServices) {
                for ((_, value1) in value) {
                    characteristics.add(value1)
                }
            }
            return characteristics
        }

    fun getReadCharacteristic(
        readService: UUID?,
        readCharacteristic: UUID?
    ): BluetoothGattCharacteristic? {
        return if (mReadServices.containsKey(readService) &&
            mReadServices[readService]!!.containsKey(readCharacteristic)
        ) {
            mReadServices[readService]!![readCharacteristic]
        } else null
    }

    fun getReadCharacteristic(readCharacteristic: UUID?): BluetoothGattCharacteristic? {
        for ((_, value) in mReadServices) {
            if (value.containsKey(readCharacteristic)) {
                return value[readCharacteristic]
            }
        }
        return null
    }

    fun containsReadCharacteristic(readCharacteristic: UUID?): Boolean {
        for ((_, value) in mReadServices) {
            if (value.containsKey(readCharacteristic)) {
                return true
            }
        }
        return false
    }

    fun addWriteService(writeService: UUID?) {
        if (!mWriteServices.containsKey(writeService)) {
            mWriteServices[writeService] = HashMap()
        }
    }

    fun containsWriteService(writeService: UUID?): Boolean {
        return mWriteServices.containsKey(writeService)
    }

    val writeServices: List<UUID?>
        get() {
            val services: MutableList<UUID?> = ArrayList()
            for ((key) in mWriteServices) {
                services.add(key)
            }
            return services
        }

    fun removeWriteService(writeService: UUID?) {
        if (mWriteServices.containsKey(writeService)) {
            mWriteServices.remove(writeService)
        }
    }

    fun addWriteCharacteristic(writeService: UUID?, writeCharacteristic: UUID?) {
        addWriteService(writeService)
        if (mWriteServices.containsKey(writeService) &&
            !mWriteServices[writeService]!!.containsKey(writeCharacteristic)
        ) {
            mWriteServices[writeService]!![writeCharacteristic] = null
        }
    }

    fun containsWriteCharacteristic(writeService: UUID?, writeCharacteristic: UUID?): Boolean {
        return mWriteServices.containsKey(writeService) &&
                mWriteServices[writeService]!!.containsKey(writeCharacteristic)
    }

    fun getWriteCharacteristics(writeService: UUID?): List<UUID?>? {
        if (mWriteServices.containsKey(writeService)) {
            val characteristics: MutableList<UUID?> = ArrayList()
            for ((key) in mWriteServices[writeService]!!) {
                characteristics.add(key)
            }
            return characteristics
        }
        return null
    }

    fun removeWriteCharacteristic(writeService: UUID?, writeCharacteristic: UUID?) {
        if (mWriteServices.containsKey(writeService) &&
            mWriteServices[writeService]!!.containsKey(writeCharacteristic)
        ) {
            mWriteServices[writeService]!!.remove(writeCharacteristic)
        }
    }

    fun setWriteCharacteristic(
        writeService: UUID?,
        writeCharacteristic: UUID?,
        gatt: BluetoothGattCharacteristic?
    ) {
        if (mWriteServices.containsKey(writeService) &&
            mWriteServices[writeService]!!.containsKey(writeCharacteristic)
        ) {
            mWriteServices[writeService]!![writeCharacteristic] = gatt
        }
    }

    val allWriteCharacteristics: List<BluetoothGattCharacteristic?>
        get() {
            val characteristics: MutableList<BluetoothGattCharacteristic?> = ArrayList()
            for ((_, value) in mWriteServices) {
                for ((_, value1) in value) {
                    characteristics.add(value1)
                }
            }
            return characteristics
        }

    fun getWriteCharacteristic(
        writeService: UUID?,
        writeCharacteristic: UUID?
    ): BluetoothGattCharacteristic? {
        return if (mWriteServices.containsKey(writeService) &&
            mWriteServices[writeService]!!.containsKey(writeCharacteristic)
        ) {
            mWriteServices[writeService]!![writeCharacteristic]
        } else null
    }

    fun getWriteCharacteristic(writeCharacteristic: UUID?): BluetoothGattCharacteristic? {
        for ((_, value) in mWriteServices) {
            if (value.containsKey(writeCharacteristic)) {
                return value[writeCharacteristic]
            }
        }
        return null
    }

    fun containsWriteCharacteristic(writeCharacteristic: UUID?): Boolean {
        for ((_, value) in mWriteServices) {
            if (value.containsKey(writeCharacteristic)) {
                return true
            }
        }
        return false
    }

    companion object {
        private val TAG = BleDeviceDelegate::class.java.name
    }
}