package com.openmobl.pttDriver.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import com.openmobl.pttDriver.model.Device;
import com.openmobl.pttDriver.model.Driver;

import java.util.ArrayList;
import java.util.List;

public class DriverSQLiteDatabase  extends SQLiteOpenHelper implements DriverDatabase {
    private static final String TAG = DriverSQLiteDatabase.class.getName();

    public static final String DB_NAME = "pttdriver.db";
    public static final int DB_VERSION = 3;

    private static final int DEVWATCH_ADD_AFTER_VER = 1;
    private static final int DEVTYPE_ADD_AFTER_VER = 2;

    private static final String TABLE_DEVICES = "devices";
    private static final String DEVICE_ID = "_id";
    private static final String DEVICE_TYPE = "type";
    private static final String DEVICE_NAME = "name";
    private static final String DEVICE_MAC = "mac";
    private static final String DEVICE_DRIVER_ID = "driver";
    private static final String DEVICE_AUTOCONNECT = "auto_connect";
    private static final String DEVICE_AUTORECONNECT = "auto_reconnect";
    private static final String DEVICE_PTTDOWN_DELAY = "ptt_down_delay";
    private static final String TABLE_DEVICES_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_DEVICES + "` ("
            + "`" + DEVICE_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + DEVICE_TYPE + "` TEXT NOT NULL,"
            + "`" + DEVICE_NAME + "` TEXT NOT NULL,"
            + "`" + DEVICE_MAC + "` TEXT NOT NULL UNIQUE,"
            + "`" + DEVICE_DRIVER_ID + "` INTEGER,"
            + "`" + DEVICE_AUTOCONNECT + "` INTEGER NOT NULL,"
            + "`" + DEVICE_AUTORECONNECT + "` INTEGER NOT NULL,"
            + "`" + DEVICE_PTTDOWN_DELAY + "` INTEGER NOT NULL"
            + ");";

    private static final String TABLE_DRIVERS = "drivers";
    private static final String DRIVER_ID = "_id";
    private static final String DRIVER_NAME = "name";
    private static final String DRIVER_TYPE = "type";
    private static final String DRIVER_DEV_NAME_MATCH = "device_name";
    private static final String DRIVER_WATCH_FOR_DEV = "device_watchfor_name";
    private static final String DRIVER_JSON = "json";
    private static final String TABLE_DRIVERS_CREATE_SQL = "CREATE TABLE IF NOT EXISTS `" + TABLE_DRIVERS + "` ("
            + "`" + DRIVER_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "`" + DRIVER_NAME + "` TEXT NOT NULL UNIQUE,"
            + "`" + DRIVER_DEV_NAME_MATCH + "` TEXT,"
            + "`" + DRIVER_WATCH_FOR_DEV + "` TEXT,"
            + "`" + DRIVER_TYPE + "` TEXT NOT NULL,"
            + "`" + DRIVER_JSON + "` TEXT NOT NULL"
            + ");";

    public DriverSQLiteDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public DriverSQLiteDatabase(Context context, String name) {
        super(context, name, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_DEVICES_CREATE_SQL);
        db.execSQL(TABLE_DRIVERS_CREATE_SQL);
    }

    private void updateTableAddColumns(SQLiteDatabase db, String table, String[] columns) {
        for (String column : columns) {
            String updateTable = "ALTER TABLE `" + table + "` ADD COLUMN "
                    + column + ";";
            db.execSQL(updateTable);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion <= DEVWATCH_ADD_AFTER_VER) {
            String[] columns = new String[] {
                    "`" + DRIVER_DEV_NAME_MATCH + "` TEXT",
                    "`" + DRIVER_WATCH_FOR_DEV + "` TEXT"
                };

            updateTableAddColumns(db, TABLE_DRIVERS, columns);
        }
        if (oldVersion <= DEVTYPE_ADD_AFTER_VER) {
            String[] columns = new String[] {
                    "`" + DEVICE_TYPE + "` TEXT NOT NULL DEFAULT `bluetooth`"
            };

            updateTableAddColumns(db, TABLE_DEVICES, columns);
        }
    }

    @Override
    public void open() {

    }

    private boolean rowExists(String table, String field, String value) {
        Cursor c = getReadableDatabase().query(
                table,
                new String[]{ field },
                field + "=?",
                new String[]{ value },
                null,
                null,
                null);

        c.moveToFirst();

        return !c.isAfterLast();
    }

    private boolean getBoolean(Cursor cursor, int columnIndex) {
        try {
            if (cursor.isNull(columnIndex)) {
                return false;
            } else if (cursor.getShort(columnIndex) != 0 ||
                        cursor.getString(columnIndex).equalsIgnoreCase("true")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();

            return false;
        }
    }

    private int getInt(Cursor cursor, int columnIndex, int defaultVal) {
        try {
            if (cursor.isNull(columnIndex)) {
                return defaultVal;
            } else {
                return cursor.getInt(columnIndex);
            }
        } catch (Exception e) {
            e.printStackTrace();

            return defaultVal;
        }
    }

    private int booleanToInt(boolean value) {
        return value ? 1 : 0;
    }

    @Override
    public List<Device> getDevices() {
        List<Device> devices = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                TABLE_DEVICES,
                new String[]{ DEVICE_ID, DEVICE_TYPE, DEVICE_NAME, DEVICE_MAC, DEVICE_DRIVER_ID,
                        DEVICE_AUTOCONNECT, DEVICE_AUTORECONNECT, DEVICE_PTTDOWN_DELAY },
                null, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Device device = new Device(cursor.getInt(cursor.getColumnIndex(DEVICE_ID)),
                    Device.DeviceType.toDeviceType(cursor.getString(cursor.getColumnIndex(DEVICE_TYPE))),
                    cursor.getString(cursor.getColumnIndex(DEVICE_NAME)),
                    cursor.getString(cursor.getColumnIndex(DEVICE_MAC)),
                    getInt(cursor, cursor.getColumnIndex(DEVICE_DRIVER_ID), -1),
                    getBoolean(cursor, cursor.getColumnIndex(DEVICE_AUTOCONNECT)),
                    getBoolean(cursor, cursor.getColumnIndex(DEVICE_AUTORECONNECT)),
                    cursor.getInt(cursor.getColumnIndex(DEVICE_PTTDOWN_DELAY)));
            devices.add(device);
            cursor.moveToNext();
        }

        cursor.close();

        return devices;
    }

    private Device getDeviceBy(String columnName, String value) {
        Cursor cursor = getReadableDatabase().query(TABLE_DEVICES,
                new String[] { DEVICE_ID, DEVICE_TYPE, DEVICE_NAME, DEVICE_MAC, DEVICE_DRIVER_ID,
                        DEVICE_AUTOCONNECT, DEVICE_AUTORECONNECT, DEVICE_PTTDOWN_DELAY },
                columnName + "=?",
                new String[] { value }, null, null, null);

        if (!cursor.moveToFirst())
            return null;

        Device device = new Device(cursor.getInt(cursor.getColumnIndex(DEVICE_ID)),
                Device.DeviceType.toDeviceType(cursor.getString(cursor.getColumnIndex(DEVICE_TYPE))),
                cursor.getString(cursor.getColumnIndex(DEVICE_NAME)),
                cursor.getString(cursor.getColumnIndex(DEVICE_MAC)),
                getInt(cursor, cursor.getColumnIndex(DEVICE_DRIVER_ID), -1),
                getBoolean(cursor, cursor.getColumnIndex(DEVICE_AUTOCONNECT)),
                getBoolean(cursor, cursor.getColumnIndex(DEVICE_AUTORECONNECT)),
                cursor.getInt(cursor.getColumnIndex(DEVICE_PTTDOWN_DELAY)));

        cursor.close();

        return device;
    }

    @Override
    public Device getDevice(int id) {
        return getDeviceBy(DEVICE_ID, String.valueOf(id));
    }

    @Override
    public Device getDevice(String macAddress) {
        return getDeviceBy(DEVICE_MAC, macAddress);
    }

    @Override
    public boolean deviceExists(int id) {
        return rowExists(TABLE_DEVICES, DEVICE_ID, String.valueOf(id));
    }

    @Override
    public boolean deviceExists(String macAddress) {
        return rowExists(TABLE_DEVICES, DEVICE_MAC, macAddress);
    }

    @Override
    public void addDevice(Device device) {
        ContentValues values = new ContentValues();

        values.put(DEVICE_NAME, device.getName());
        values.put(DEVICE_TYPE, device.getDeviceType().toString());
        values.put(DEVICE_MAC, device.getMacAddress());
        values.put(DEVICE_DRIVER_ID, device.getDriverId());
        values.put(DEVICE_AUTOCONNECT, booleanToInt(device.getAutoConnect()));
        values.put(DEVICE_AUTORECONNECT, booleanToInt(device.getAutoReconnect()));
        values.put(DEVICE_PTTDOWN_DELAY, device.getPttDownDelay());

        device.setId((int)getWritableDatabase().insert(TABLE_DEVICES, null, values));
    }

    @Override
    public void addOrUpdateDevice(Device device) {
        if (device.getId() >= 0 && driverExists(device.getId()) ||
                driverExists(device.getMacAddress())) {
            updateDevice(device);
        } else {
            addDevice(device);
        }
    }

    @Override
    public void updateDevice(Device device) {
        ContentValues values = new ContentValues();

        values.put(DEVICE_NAME, device.getName());
        values.put(DEVICE_TYPE, device.getDeviceType().toString());
        values.put(DEVICE_MAC, device.getMacAddress());
        values.put(DEVICE_DRIVER_ID, device.getDriverId());
        values.put(DEVICE_AUTOCONNECT, booleanToInt(device.getAutoConnect()));
        values.put(DEVICE_AUTORECONNECT, booleanToInt(device.getAutoReconnect()));
        values.put(DEVICE_PTTDOWN_DELAY, device.getPttDownDelay());

        getWritableDatabase().update(
                TABLE_DEVICES,
                values,
                DEVICE_ID + "=?",
                new String[]{ Integer.toString(device.getId()) });
    }

    @Override
    public void removeDevice(int id) {
        if (id > 0) {
            getWritableDatabase().delete(TABLE_DEVICES, DEVICE_ID + "=?",
                    new String[]{ String.valueOf(id) });
        }
    }

    @Override
    public void removeDevice(Device device) {
        int id = device.getId();

        if (id > 0) {
            removeDevice(id);
        }
    }

    @Override
    public List<Driver> getDrivers() {
        List<Driver> drivers = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                TABLE_DRIVERS,
                new String[]{
                        DRIVER_ID, DRIVER_NAME, DRIVER_TYPE,
                        DRIVER_DEV_NAME_MATCH, DRIVER_WATCH_FOR_DEV, DRIVER_JSON
                    },
                null, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Driver driver = new Driver(cursor.getInt(cursor.getColumnIndex(DRIVER_ID)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_NAME)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_TYPE)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_JSON)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_DEV_NAME_MATCH)),
                    cursor.getString(cursor.getColumnIndex(DRIVER_WATCH_FOR_DEV)));
            drivers.add(driver);
            cursor.moveToNext();
        }

        cursor.close();

        return drivers;
    }

    private Driver getDriverBy(String columnName, String value) {
        Cursor cursor = getReadableDatabase().query(TABLE_DRIVERS,
                new String[] {
                        DRIVER_ID, DRIVER_NAME, DRIVER_TYPE,
                        DRIVER_DEV_NAME_MATCH, DRIVER_WATCH_FOR_DEV, DRIVER_JSON
                    },
                columnName + "=?",
                new String[] { value }, null, null, null);

        if (!cursor.moveToFirst())
            return null;

        Driver driver = new Driver(cursor.getInt(cursor.getColumnIndex(DRIVER_ID)),
                cursor.getString(cursor.getColumnIndex(DRIVER_NAME)),
                cursor.getString(cursor.getColumnIndex(DRIVER_TYPE)),
                cursor.getString(cursor.getColumnIndex(DRIVER_JSON)),
                cursor.getString(cursor.getColumnIndex(DRIVER_DEV_NAME_MATCH)),
                cursor.getString(cursor.getColumnIndex(DRIVER_WATCH_FOR_DEV)));

        cursor.close();

        return driver;
    }

    @Override
    public Driver getDriver(int id) {
        return getDriverBy(DRIVER_ID, String.valueOf(id));
    }

    @Override
    public Driver getDriver(String name) {
        return getDriverBy(DRIVER_NAME, name);
    }

    @Override
    public boolean driverExists(int id) {
        return rowExists(TABLE_DRIVERS, DRIVER_ID, String.valueOf(id));
    }

    @Override
    public boolean driverExists(String name) {
        return rowExists(TABLE_DRIVERS, DRIVER_NAME, name);
    }

    @Override
    public void addDriver(Driver driver) {
        ContentValues values = new ContentValues();

        values.put(DRIVER_NAME, driver.getName());
        values.put(DRIVER_TYPE, driver.getType());
        values.put(DRIVER_JSON, driver.getJson());
        values.put(DRIVER_DEV_NAME_MATCH, driver.getDeviceNameMatch());
        values.put(DRIVER_WATCH_FOR_DEV, driver.getWatchForDeviceName());


        driver.setId((int)getWritableDatabase().insert(TABLE_DRIVERS, null, values));
    }

    @Override
    public void addOrUpdateDriver(Driver driver) {
        if (driver.getId() >= 0 && driverExists(driver.getId()) ||
            driverExists(driver.getName())) {
            updateDriver(driver);
        } else {
            addDriver(driver);
        }
    }

    @Override
    public void updateDriver(Driver driver) {
        ContentValues values = new ContentValues();

        values.put(DRIVER_NAME, driver.getName());
        values.put(DRIVER_TYPE, driver.getType());
        values.put(DRIVER_JSON, driver.getJson());
        values.put(DRIVER_DEV_NAME_MATCH, driver.getDeviceNameMatch());
        values.put(DRIVER_WATCH_FOR_DEV, driver.getWatchForDeviceName());

        getWritableDatabase().update(
                TABLE_DRIVERS,
                values,
                DRIVER_ID + "=?",
                new String[]{ Integer.toString(driver.getId()) });
    }

    @Override
    public void removeDriver(int id) {
        if (id > 0) {
            getWritableDatabase().delete(TABLE_DRIVERS, DRIVER_ID + "=?",
                    new String[]{ String.valueOf(id) });

            // Remove References
            ContentValues values = new ContentValues();

            values.put(DEVICE_DRIVER_ID, -1);

            getWritableDatabase().update(
                    TABLE_DEVICES,
                    values,
                    DEVICE_DRIVER_ID + "=?",
                    new String[]{ Integer.toString(id) });
        }
    }

    @Override
    public void removeDriver(Driver driver) {
        int id = driver.getId();

        if (id > 0) {
            removeDriver(id);
        }
    }
}
