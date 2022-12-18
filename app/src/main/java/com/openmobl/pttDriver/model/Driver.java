package com.openmobl.pttDriver.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class Driver implements Record, Parcelable {
    private static final String TAG = Driver.class.getName();

    private int mId;
    private String mName;
    private String mType;
    private String mJson;
    private String mDeviceNameMatch;
    private String mWatchForDeviceName;

    public static final Parcelable.Creator<Driver> CREATOR = new Parcelable.Creator<Driver>() {

        @Override
        public Driver createFromParcel(Parcel parcel) {
            return new Driver(parcel);
        }

        @Override
        public Driver[] newArray(int i) {
            return new Driver[i];
        }
    };

    public Driver(String name, String json) {
        mId = -1;
        mName = name;
        mType = ""; // read from JSON
        mJson = json; // validate JSON
        mDeviceNameMatch = null;
        mWatchForDeviceName = null;
    }
    public Driver(int id, String name, String json) {
        this(name, json);

        mId = id;
    }
    public Driver(String name, String type, String json) {
        this(name, json);

        mType = type;
    }
    public Driver(int id, String name, String type, String json) {
        this(id, name, json);

        mType = type;
    }
    public Driver(String name, String type, String json, String deviceNameMatch, String watchForDeviceName) {
        this(name, json);

        mType = type;
        mDeviceNameMatch = deviceNameMatch;
        mWatchForDeviceName = watchForDeviceName;
    }
    public Driver(int id, String name, String type, String json, String deviceNameMatch, String watchForDeviceName) {
        this(id, name, json);

        mType = type;
        mDeviceNameMatch = deviceNameMatch;
        mWatchForDeviceName = watchForDeviceName;
    }

    private Driver(Parcel parcel) {
        readFromParcel(parcel);
    }

    public void setId(int id) { mId = id; }

    @Override
    public int getId() { return mId; }
    @Override
    public String getName() { return mName; }
    public String getType() { return mType; }
    public String getJson() { return mJson; }
    public String getDeviceNameMatch() { return mDeviceNameMatch; }
    public String getWatchForDeviceName() { return mWatchForDeviceName; }
    @Override
    public String getDetails() {
        return "";
    }

    @Override
    public RecordType getRecordType() {
        return RecordType.DRIVER;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mId);
        parcel.writeString(mName);
        parcel.writeString(mType);
        parcel.writeString(mJson);
        parcel.writeString(mDeviceNameMatch);
        parcel.writeString(mWatchForDeviceName);
    }

    private void readFromParcel(Parcel parcel) {
        mId = parcel.readInt();
        mName = parcel.readString();
        mType = parcel.readString();
        mJson = parcel.readString();
        mDeviceNameMatch = parcel.readString();
        mWatchForDeviceName = parcel.readString();
    }

    public static Driver getEmptyDriver() {
        return new Driver("", "");
    }

    @NonNull
    @Override
    public String toString() {
        return getName();
    }

    public String toStringFull() {
        return "Driver(" + getId() + ", " + getName() + ", " + getType() + ", JSON String)";
    }
}
