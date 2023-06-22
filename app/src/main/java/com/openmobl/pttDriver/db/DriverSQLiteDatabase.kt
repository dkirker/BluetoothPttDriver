package com.openmobl.pttDriver.db

import android.content.*
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.openmobl.pttDriver.db.DriverSQLiteDatabase
import com.openmobl.pttDriver.model.*
import com.openmobl.pttDriver.model.Device.DeviceType

class DriverSQLiteDatabase : SQLiteOpenHelper, DriverDatabase {
    constructor(context: Context?) : super(context, DB_NAME, null, DB_VERSION) {}
    constructor(context: Context?, name: String?) : super(context, name, null, DB_VERSION) {}

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(TABLE_DEVICES_CREATE_SQL)
        db.execSQL(TABLE_DRIVERS_CREATE_SQL)
    }

    private fun updateTableAddColumns(db: SQLiteDatabase, table: String, columns: Array<String>) {
        for (column in columns) {
            val updateTable = ("ALTER TABLE `" + table + "` ADD COLUMN "
                    + column + ";")
            db.execSQL(updateTable)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion <= DEVWATCH_ADD_AFTER_VER) {
            val columns = arrayOf(
                "`" + DRIVER_DEV_NAME_MATCH + "` TEXT",
                "`" + DRIVER_WATCH_FOR_DEV + "` TEXT"
            )
            updateTableAddColumns(db, TABLE_DRIVERS, columns)
        }
        if (oldVersion <= DEVTYPE_ADD_AFTER_VER) {
            val columns = arrayOf(
                "`" + DEVICE_TYPE + "` TEXT NOT NULL DEFAULT `bluetooth`"
            )
            updateTableAddColumns(db, TABLE_DEVICES, columns)
        }
    }

    override fun open() {}
    private fun rowExists(table: String, field: String, value: String?): Boolean {
        val c = readableDatabase.query(
            table, arrayOf(field),
            "$field=?", arrayOf(value),
            null,
            null,
            null
        )
        c.moveToFirst()
        return !c.isAfterLast
    }

    private fun getBoolean(cursor: Cursor, columnIndex: Int): Boolean {
        return try {
            if (cursor.isNull(columnIndex)) {
                false
            } else if (cursor.getShort(columnIndex).toInt() != 0 ||
                cursor.getString(columnIndex).equals("true", ignoreCase = true)
            ) {
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getInt(cursor: Cursor, columnIndex: Int, defaultVal: Int): Int {
        return try {
            if (cursor.isNull(columnIndex)) {
                defaultVal
            } else {
                cursor.getInt(columnIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            defaultVal
        }
    }

    private fun booleanToInt(value: Boolean): Int {
        return if (value) 1 else 0
    }

    override val devices: List<Device>
        get() {
            val devices: MutableList<Device> = ArrayList()
            val cursor = readableDatabase.query(
                TABLE_DEVICES, arrayOf(
                    DEVICE_ID, DEVICE_TYPE, DEVICE_NAME, DEVICE_MAC, DEVICE_DRIVER_ID,
                    DEVICE_AUTOCONNECT, DEVICE_AUTORECONNECT, DEVICE_PTTDOWN_DELAY
                ),
                null, null, null, null, null
            )
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val device = Device(
                    cursor.getInt(cursor.getColumnIndex(DEVICE_ID)),
                    DeviceType.Companion.toDeviceType(
                        cursor.getString(
                            cursor.getColumnIndex(
                                DEVICE_TYPE
                            )
                        )
                    ),
                    cursor.getString(cursor.getColumnIndex(DEVICE_NAME)),
                    cursor.getString(cursor.getColumnIndex(DEVICE_MAC)),
                    getInt(cursor, cursor.getColumnIndex(DEVICE_DRIVER_ID), -1),
                    getBoolean(cursor, cursor.getColumnIndex(DEVICE_AUTOCONNECT)),
                    getBoolean(cursor, cursor.getColumnIndex(DEVICE_AUTORECONNECT)),
                    cursor.getInt(cursor.getColumnIndex(DEVICE_PTTDOWN_DELAY))
                )
                devices.add(device)
                cursor.moveToNext()
            }
            cursor.close()
            return devices
        }

    private fun getDeviceBy(columnName: String, value: String): Device? {
        val cursor = readableDatabase.query(
            TABLE_DEVICES, arrayOf(
                DEVICE_ID, DEVICE_TYPE, DEVICE_NAME, DEVICE_MAC, DEVICE_DRIVER_ID,
                DEVICE_AUTOCONNECT, DEVICE_AUTORECONNECT, DEVICE_PTTDOWN_DELAY
            ),
            "$columnName=?", arrayOf(value), null, null, null
        )
        if (!cursor.moveToFirst()) return null
        val device = Device(
            cursor.getInt(cursor.getColumnIndex(DEVICE_ID)),
            DeviceType.Companion.toDeviceType(cursor.getString(cursor.getColumnIndex(DEVICE_TYPE))),
            cursor.getString(cursor.getColumnIndex(DEVICE_NAME)),
            cursor.getString(cursor.getColumnIndex(DEVICE_MAC)),
            getInt(cursor, cursor.getColumnIndex(DEVICE_DRIVER_ID), -1),
            getBoolean(cursor, cursor.getColumnIndex(DEVICE_AUTOCONNECT)),
            getBoolean(cursor, cursor.getColumnIndex(DEVICE_AUTORECONNECT)),
            cursor.getInt(cursor.getColumnIndex(DEVICE_PTTDOWN_DELAY))
        )
        cursor.close()
        return device
    }

    override fun getDevice(id: Int): Device? {
        return getDeviceBy(DEVICE_ID, id.toString())
    }

    override fun getDevice(macAddress: String): Device? {
        return getDeviceBy(DEVICE_MAC, macAddress)
    }

    override fun deviceExists(id: Int): Boolean {
        return rowExists(TABLE_DEVICES, DEVICE_ID, id.toString())
    }

    override fun deviceExists(macAddress: String?): Boolean {
        return rowExists(TABLE_DEVICES, DEVICE_MAC, macAddress)
    }

    override fun addDevice(device: Device) {
        val values = ContentValues()
        values.put(DEVICE_NAME, device.name)
        values.put(DEVICE_TYPE, device.deviceType.toString())
        values.put(DEVICE_MAC, device.macAddress)
        values.put(DEVICE_DRIVER_ID, device.driverId)
        values.put(DEVICE_AUTOCONNECT, booleanToInt(device.autoConnect))
        values.put(DEVICE_AUTORECONNECT, booleanToInt(device.autoReconnect))
        values.put(DEVICE_PTTDOWN_DELAY, device.pttDownDelay)
        device.id =
            writableDatabase.insert(TABLE_DEVICES, null, values).toInt()
    }

    override fun addOrUpdateDevice(device: Device) {
        if (device.id >= 0 && driverExists(device.id) ||
            driverExists(device.macAddress)
        ) {
            updateDevice(device)
        } else {
            addDevice(device)
        }
    }

    override fun updateDevice(device: Device) {
        val values = ContentValues()
        values.put(DEVICE_NAME, device.name)
        values.put(DEVICE_TYPE, device.deviceType.toString())
        values.put(DEVICE_MAC, device.macAddress)
        values.put(DEVICE_DRIVER_ID, device.driverId)
        values.put(DEVICE_AUTOCONNECT, booleanToInt(device.autoConnect))
        values.put(DEVICE_AUTORECONNECT, booleanToInt(device.autoReconnect))
        values.put(DEVICE_PTTDOWN_DELAY, device.pttDownDelay)
        writableDatabase.update(
            TABLE_DEVICES,
            values,
            DEVICE_ID + "=?", arrayOf(Integer.toString(device.id))
        )
    }

    override fun removeDevice(id: Int) {
        if (id > 0) {
            writableDatabase.delete(TABLE_DEVICES, DEVICE_ID + "=?", arrayOf(id.toString()))
        }
    }

    override fun removeDevice(device: Device?) {
        val id = device.getId()
        if (id > 0) {
            removeDevice(id)
        }
    }

    override val drivers: List<Driver>
        get() {
            val drivers: MutableList<Driver> = ArrayList()
            val cursor = readableDatabase.query(
                TABLE_DRIVERS, arrayOf(
                    DRIVER_ID, DRIVER_NAME, DRIVER_TYPE,
                    DRIVER_DEV_NAME_MATCH, DRIVER_WATCH_FOR_DEV, DRIVER_JSON
                ),
                null, null, null, null, null
            )
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val driver = Driver(
                    cursor.getInt(cursor.getColumnIndex(DRIVER_ID)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_NAME)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_TYPE)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_JSON)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_DEV_NAME_MATCH)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_WATCH_FOR_DEV))
                )
                drivers.add(driver)
                cursor.moveToNext()
            }
            cursor.close()
            return drivers
        }

    private fun getDriverBy(columnName: String, value: String): Driver? {
        val cursor = readableDatabase.query(
            TABLE_DRIVERS, arrayOf(
                DRIVER_ID, DRIVER_NAME, DRIVER_TYPE,
                DRIVER_DEV_NAME_MATCH, DRIVER_WATCH_FOR_DEV, DRIVER_JSON
            ),
            "$columnName=?", arrayOf(value), null, null, null
        )
        if (!cursor.moveToFirst()) return null
        val driver = Driver(
            cursor.getInt(cursor.getColumnIndex(DRIVER_ID)),
            cursor.getString(cursor.getColumnIndex(DRIVER_NAME)),
            cursor.getString(cursor.getColumnIndex(DRIVER_TYPE)),
            cursor.getString(cursor.getColumnIndex(DRIVER_JSON)),
            cursor.getString(cursor.getColumnIndex(DRIVER_DEV_NAME_MATCH)),
            cursor.getString(cursor.getColumnIndex(DRIVER_WATCH_FOR_DEV))
        )
        cursor.close()
        return driver
    }

    override fun getDriver(id: Int): Driver? {
        return getDriverBy(DRIVER_ID, id.toString())
    }

    override fun getDriver(name: String): Driver? {
        return getDriverBy(DRIVER_NAME, name)
    }

    override fun driverExists(id: Int): Boolean {
        return rowExists(TABLE_DRIVERS, DRIVER_ID, id.toString())
    }

    override fun driverExists(name: String?): Boolean {
        return rowExists(TABLE_DRIVERS, DRIVER_NAME, name)
    }

    override fun addDriver(driver: Driver) {
        val values = ContentValues()
        values.put(DRIVER_NAME, driver.name)
        values.put(DRIVER_TYPE, driver.type)
        values.put(DRIVER_JSON, driver.json)
        values.put(DRIVER_DEV_NAME_MATCH, driver.deviceNameMatch)
        values.put(DRIVER_WATCH_FOR_DEV, driver.watchForDeviceName)
        driver.id =
            writableDatabase.insert(TABLE_DRIVERS, null, values).toInt()
    }

    override fun addOrUpdateDriver(driver: Driver) {
        if (driver.id >= 0 && driverExists(driver.id) ||
            driverExists(driver.name)
        ) {
            updateDriver(driver)
        } else {
            addDriver(driver)
        }
    }

    override fun updateDriver(driver: Driver) {
        val values = ContentValues()
        values.put(DRIVER_NAME, driver.name)
        values.put(DRIVER_TYPE, driver.type)
        values.put(DRIVER_JSON, driver.json)
        values.put(DRIVER_DEV_NAME_MATCH, driver.deviceNameMatch)
        values.put(DRIVER_WATCH_FOR_DEV, driver.watchForDeviceName)
        writableDatabase.update(
            TABLE_DRIVERS,
            values,
            DRIVER_ID + "=?", arrayOf(Integer.toString(driver.id))
        )
    }

    override fun removeDriver(id: Int) {
        if (id > 0) {
            writableDatabase.delete(TABLE_DRIVERS, DRIVER_ID + "=?", arrayOf(id.toString()))

            // Remove References
            val values = ContentValues()
            values.put(DEVICE_DRIVER_ID, -1)
            writableDatabase.update(
                TABLE_DEVICES,
                values,
                DEVICE_DRIVER_ID + "=?", arrayOf(Integer.toString(id))
            )
        }
    }

    override fun removeDriver(driver: Driver?) {
        val id = driver.getId()
        if (id > 0) {
            removeDriver(id)
        }
    }

    companion object {
        private val TAG = DriverSQLiteDatabase::class.java.name
        const val DB_NAME = "pttdriver.db"
        const val DB_VERSION = 3
        private const val DEVWATCH_ADD_AFTER_VER = 1
        private const val DEVTYPE_ADD_AFTER_VER = 2
        private const val TABLE_DEVICES = "devices"
        private const val DEVICE_ID = "_id"
        private const val DEVICE_TYPE = "type"
        private const val DEVICE_NAME = "name"
        private const val DEVICE_MAC = "mac"
        private const val DEVICE_DRIVER_ID = "driver"
        private const val DEVICE_AUTOCONNECT = "auto_connect"
        private const val DEVICE_AUTORECONNECT = "auto_reconnect"
        private const val DEVICE_PTTDOWN_DELAY = "ptt_down_delay"
        private const val TABLE_DEVICES_CREATE_SQL =
            ("CREATE TABLE IF NOT EXISTS `" + TABLE_DEVICES + "` ("
                    + "`" + DEVICE_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "`" + DEVICE_TYPE + "` TEXT NOT NULL,"
                    + "`" + DEVICE_NAME + "` TEXT NOT NULL,"
                    + "`" + DEVICE_MAC + "` TEXT NOT NULL UNIQUE,"
                    + "`" + DEVICE_DRIVER_ID + "` INTEGER,"
                    + "`" + DEVICE_AUTOCONNECT + "` INTEGER NOT NULL,"
                    + "`" + DEVICE_AUTORECONNECT + "` INTEGER NOT NULL,"
                    + "`" + DEVICE_PTTDOWN_DELAY + "` INTEGER NOT NULL"
                    + ");")
        private const val TABLE_DRIVERS = "drivers"
        private const val DRIVER_ID = "_id"
        private const val DRIVER_NAME = "name"
        private const val DRIVER_TYPE = "type"
        private const val DRIVER_DEV_NAME_MATCH = "device_name"
        private const val DRIVER_WATCH_FOR_DEV = "device_watchfor_name"
        private const val DRIVER_JSON = "json"
        private const val TABLE_DRIVERS_CREATE_SQL =
            ("CREATE TABLE IF NOT EXISTS `" + TABLE_DRIVERS + "` ("
                    + "`" + DRIVER_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "`" + DRIVER_NAME + "` TEXT NOT NULL UNIQUE,"
                    + "`" + DRIVER_DEV_NAME_MATCH + "` TEXT,"
                    + "`" + DRIVER_WATCH_FOR_DEV + "` TEXT,"
                    + "`" + DRIVER_TYPE + "` TEXT NOT NULL,"
                    + "`" + DRIVER_JSON + "` TEXT NOT NULL"
                    + ");")
    }
}