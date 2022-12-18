package com.openmobl.pttDriver.model;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class Device implements Record, Parcelable {
    private static final String TAG = Device.class.getName();

    private int mId;
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

    public Device(String name, String macAddress,
                  boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        mId = -1;
        mName = name;
        mMacAddress = macAddress;
        mDriverId = -1;
        mDriverName = "";
        mAutoConnect = autoConnect;
        mAutoReconnect = autoReconnect;
        mPttDownDelay = pttDownDelay;
    }

    public Device(int id, String name, String macAddress,
                  boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        this(name, macAddress, autoConnect, autoReconnect, pttDownDelay);

        mId = id;
    }
    public Device(String name, String macAddress,
                  int driverId, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        this(name, macAddress, autoConnect, autoReconnect, pttDownDelay);

        mDriverId = driverId;
    }
    public Device(int id, String name, String macAddress,
                  int driverId, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        this(id, name, macAddress, autoConnect, autoReconnect, pttDownDelay);

        mDriverId = driverId;
    }
    public Device(int id, String name, String macAddress,
                  int driverId, String driverName, boolean autoConnect, boolean autoReconnect, int pttDownDelay) {
        this(id, name, macAddress, driverId, autoConnect, autoReconnect, pttDownDelay);

        mDriverName = driverName;
    }

    private Device(Parcel parcel) {
        readFromParcel(parcel);
    }

    public void setId(int id) { mId = id; }

    @Override
    public int getId() { return mId; }
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
        return "Device(" + getId() + ", " + getName() + ", " + getMacAddress() +
                    ", " + getDriverId() + ", " + getDriverName() + ", " +
                    getAutoConnect() + ", " + getAutoReconnect() + ", " + getPttDownDelay() + ")";
    }
}
