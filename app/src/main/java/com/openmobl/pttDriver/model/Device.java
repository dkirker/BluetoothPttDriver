package com.openmobl.pttDriver.model;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class Device implements Record, Parcelable {
    private static final String TAG = Device.class.getName();

    public enum DeviceType {
        INVALID("-"),
        BLUETOOTH("bluetooth"),
        LOCAL("local");

        private final String mType;

        DeviceType(final String type) {
            mType = type;
        }
        public static DeviceType toDeviceType(String value) {
            for (DeviceType typeEnum : values()) {
                if (value.equals(typeEnum.toString()))
                    return typeEnum;
            }
            return INVALID;
        }
        public boolean isValid() {
            return this != INVALID;
        }
        @NonNull
        @Override
        public String toString() {
            return mType;
        }
    }

    private int mId;
    private DeviceType mDeviceType;
    private String mName;
    private String mMacAddress;
    private String mDriverName;
    private int mDriverId;
    private boolean mAutoConnect;
    private boolean mAutoReconnect;
    private int mPttDownDelay;

    public static final Parcelable.Creator<Device> CREATOR = new Parcelable.Creator<Device>() {

        @Override
        public Device createFromParcel(Parcel parcel) {
            return new Device(parcel);
        }

        @Override
        public Device[] newArray(int i) {
            return new Device[i];
        }
    };

    public Device(DeviceType type, String name, String macAddress,
                  boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        mId = -1;
        mDeviceType = type;
        mName = name;
        mMacAddress = macAddress;
        mDriverId = -1;
        mDriverName = "";
        mAutoConnect = autoConnect;
        mAutoReconnect = autoReconnect;
        mPttDownDelay = pttDownDelay;
    }

    public Device(int id, DeviceType type, String name, String macAddress,
                  boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        this(type, name, macAddress, autoConnect, autoReconnect, pttDownDelay);

        mId = id;
    }
    public Device(DeviceType type, String name, String macAddress,
                  int driverId, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        this(type, name, macAddress, autoConnect, autoReconnect, pttDownDelay);

        mDriverId = driverId;
    }
    public Device(int id, DeviceType type, String name, String macAddress,
                  int driverId, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        this(id, type, name, macAddress, autoConnect, autoReconnect, pttDownDelay);

        mDriverId = driverId;
    }
    public Device(int id, DeviceType type, String name, String macAddress,
                  int driverId, String driverName, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        this(id, type, name, macAddress, driverId, autoConnect, autoReconnect, pttDownDelay);

        mDriverName = driverName;
    }

    private Device(Parcel parcel) {
        readFromParcel(parcel);
    }

    public void setId(int id) { mId = id; }

    @Override
    public int getId() { return mId; }
    public DeviceType getDeviceType() { return mDeviceType; }
    @Override
    public String getName() { return mName; }
    public String getMacAddress() { return mMacAddress; }
    public int getDriverId() { return mDriverId; }
    public String getDriverName() { return mDriverName; }
    public boolean getAutoConnect() { return mAutoConnect; }
    public static boolean getAutoConnectDefault() { return true; }
    public boolean getAutoReconnect() { return mAutoReconnect; }
    public static boolean getAutoReconnectDefault() { return true; }
    public int getPttDownDelay() { return mPttDownDelay; }
    public static int getPttDownDelayDefault() { return 0; }
    @Override
    public String getDetails() {
        return "";
    }

    @Override
    public RecordType getRecordType() {
        return RecordType.DEVICE;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mId);
        parcel.writeString(mDeviceType.toString());
        parcel.writeString(mName);
        parcel.writeString(mMacAddress);
        parcel.writeInt(mDriverId);
        parcel.writeString(mDriverName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parcel.writeBoolean(mAutoConnect);
            parcel.writeBoolean(mAutoReconnect);
        } else {
            parcel.writeInt(mAutoConnect ? 1 : 0);
            parcel.writeInt(mAutoReconnect ? 1 : 0);
        }
        parcel.writeInt(mPttDownDelay);
    }

    private void readFromParcel(Parcel parcel) {
        mId = parcel.readInt();
        mDeviceType = DeviceType.toDeviceType(parcel.readString());
        mName = parcel.readString();
        mMacAddress = parcel.readString();
        mDriverId = parcel.readInt();
        mDriverName = parcel.readString();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mAutoConnect = parcel.readBoolean();
            mAutoReconnect = parcel.readBoolean();
        } else {
            mAutoConnect = parcel.readInt() != 0;
            mAutoReconnect = parcel.readInt() != 0;
        }
        mPttDownDelay = parcel.readInt();
    }

    @NonNull
    @Override
    public String toString() {
        return getName() + " (" + getMacAddress() +")";
    }

    public String toStringFull() {
        return "Device(" + getId() + ", " + getDeviceType() + ", " + getName() + ", " + getMacAddress() +
                    ", " + getDriverId() + ", " + getDriverName() + ", " +
                    getAutoConnect() + ", " + getAutoReconnect() + ", " + getPttDownDelay() + ")";
    }
}
