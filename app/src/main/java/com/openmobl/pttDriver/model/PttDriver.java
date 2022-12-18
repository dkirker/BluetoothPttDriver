package com.openmobl.pttDriver.model;

import android.content.Context;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PttDriver {
    private static final String TAG = PttDriver.class.getName();

    public enum ConnectionType {
        INVALID("-", "Invalid"),
        BLE("ble", "BLE"),
        BLE_SERIAL("ble-serial", "BLE Serial"),
        SPP("spp", "Serial"),
        HFP("hfp", "Hands-Free");

        private final String mConnectionType;
        private final String mTypeName;

        ConnectionType(final String connectionType, final String typeName) {
            mConnectionType = connectionType;
            mTypeName = typeName;
        }
        public static ConnectionType toConnectionType(String value) {
            for (ConnectionType connType : values()) {
                if (value.equals(connType.toString()))
                    return connType;
            }
            return INVALID;
        }
        public boolean isValid() {
            return this != INVALID;
        }
        @NonNull
        @Override
        public String toString() {
            return mConnectionType;
        }
        public String toHumanReadableString() {
            return mTypeName;
        }
    }
    public enum DataType {
        INVALID("-"),
        ASCII("ascii"),
        HEX("hex");

        private final String mDataType;

        DataType(final String dataType) {
            mDataType = dataType;
        }
        public static DataType toDataType(String value) {
            for (DataType dataTypeEnum : values()) {
                if (value.equals(dataTypeEnum.toString()))
                    return dataTypeEnum;
            }
            return INVALID;
        }
        public boolean isValid() {
            return this != INVALID;
        }
        @NonNull
        @Override
        public String toString() {
            return mDataType;
        }
    }
    public class IntentMap extends HashMap<String, String> { }

    private JsonReader mReader;

    private List<String> mValidationErrors;

    // Fields
    private String mDriverName;
    private String mDeviceName;
    private String mWatchForDeviceName;
    private ConnectionType mType;
    private PttWriteObj mWriteObj;
    private PttReadObj mReadObj;

    public PttDriver() {
        mValidationErrors = new ArrayList<>();
    }
    public PttDriver(Context context, Uri content) throws IOException {
        this();
        read(context, content);
    }
    public PttDriver(Context context, String json) throws IOException {
        this();
        read(context, json);
    }
    public PttDriver(JsonReader reader) throws IOException {
        this();
        read(reader);
    }

    public void read(@NonNull Context context, Uri content) throws IOException {
        InputStream inputStream =
                context.getContentResolver().openInputStream(content);
        if (inputStream != null) {
            InputStreamReader streamReader = new InputStreamReader(inputStream, "UTF-8");

            JsonReader reader = new JsonReader(streamReader);

            read(reader);
        } else {
            Log.d(TAG, "Failed to open InputStream for " + content);
            throw new IOException("Failed to open InputStream for " + content);
        }
    }
    public void read(@NonNull Context context, String json) throws IOException {
        InputStream inputStream = new ByteArrayInputStream(json.getBytes());
        if (inputStream != null) {
            InputStreamReader streamReader = new InputStreamReader(inputStream, "UTF-8");

            JsonReader reader = new JsonReader(streamReader);

            read(reader);
        } else {
            Log.d(TAG, "Failed to open InputStream for JSON String");
            throw new IOException("Failed to open InputStream for JSON String");
        }
    }
    public void read(@NonNull JsonReader reader) throws IOException {
        mReader = reader;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();

            Log.v(TAG, "Reading property: " + name);

            switch (name) {
                case "name":
                    mDriverName = reader.nextString();
                    Log.v(TAG, mDriverName);
                    break;
                case "deviceName":
                    mDeviceName = reader.nextString();
                    Log.v(TAG, mDeviceName);
                    break;
                case "watchForDeviceName":
                    mWatchForDeviceName = reader.nextString();
                    Log.v(TAG, mWatchForDeviceName);
                    break;
                case "type":
                    mType = ConnectionType.toConnectionType(reader.nextString());
                    Log.v(TAG, mType.toString());
                    break;
                case "write":
                    if (reader.peek() != JsonToken.NULL) {
                        Log.v(TAG, "Creating PttWriteObj");
                        mWriteObj = new PttWriteObj(reader);
                    } else {
                        Log.v(TAG, "write tag was null");
                    }
                    break;
                case "read":
                    if (reader.peek() != JsonToken.NULL) {
                        Log.v(TAG, "Creating PttReadObj");
                        mReadObj = new PttReadObj(reader);
                    } else {
                        Log.v(TAG, "read tag was null");
                    }
                    break;
                default:
                    Log.d(TAG, "Skipping parameter: " + name);
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
    }

    private List<String> readStringList(JsonReader reader) throws IOException {
        List<String> stringList = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            String value = reader.nextString();

            Log.v(TAG, "Adding intent to de-dupe list: " + value);

            stringList.add(value);
        }
        reader.endArray();

        return stringList;
    }

    private IntentMap readIntentMap(JsonReader reader) throws IOException {
        IntentMap intentMap = new IntentMap();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            String value = reader.nextString();

            Log.v(TAG, "Intent mapping: " + name + " -> " + value);

            intentMap.put(name, value);
        }
        reader.endObject();

        return intentMap;
    }

    private Map<UUID, IntentMap> readCharacteristicIntentMaps(JsonReader reader) throws IOException {
        Map<UUID, IntentMap> intentMap = new HashMap<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            IntentMap value = null;

            if (reader.peek() != JsonToken.NULL) {
                value = readIntentMap(reader);
            }

            try {
                intentMap.put(UUID.fromString(name), value);
            } catch (Exception e) {
                Log.v(TAG, "Could not insert intent map: " + e);
            }
        }
        reader.endObject();

        return intentMap;
    }

    public Map<String, List<String>> getValidationErrors() {
        HashMap<String, List<String>> results = new HashMap<>();

        results.put("driver", Collections.unmodifiableList(mValidationErrors));
        results.put("read", mReadObj.getValidationErrors());
        if (mWriteObj != null)
            results.put("write", mWriteObj.getValidationErrors());

        return results;
    }

    public boolean isValid() {
        boolean valid = true;

        mValidationErrors.clear();

        if (getDriverName() == null) {
            valid = false;
            mValidationErrors.add("\'driverName\' must not be null");
        }
        if (getDeviceName() == null) {
            valid = false;
            mValidationErrors.add("\'deviceName\' must not be null");
        }

        if (getType() == null) {
            valid = false;
            mValidationErrors.add("\'type\' must not be null");
        } else if (!getType().isValid()) {
            valid = false;
            mValidationErrors.add("\'driverName\' must not be null");
        }

        if (getWriteObj() != null && !getWriteObj().isValid()) {
            valid = false;
            mValidationErrors.add("\'write\' is not valid");
        }

        if (getReadObj() == null) {
            valid = false;
            mValidationErrors.add("\'read\' must not be null");
        } else if (!getReadObj().isValid()) {
            valid = false;
            mValidationErrors.add("\'read\' is not valid");
        }

        return valid;
    }

    public String getDriverName() { return mDriverName; }
    public String getDeviceName() { return mDeviceName; }
    public String getWatchForDeviceName() { return mWatchForDeviceName; }
    public ConnectionType getType() { return mType; }
    public PttWriteObj getWriteObj() { return mWriteObj; }
    public PttReadObj getReadObj() { return mReadObj; }

    public String toJsonString() {
        StringBuilder json = new StringBuilder();

        json.append("{");

        json.append("\"name\":\"" + getDriverName() + "\",");
        json.append("\"deviceName\":\"" + getDeviceName() + "\",");

        if (getWatchForDeviceName() != null) {
            json.append("\"watchForDeviceName\":\"" + getWatchForDeviceName() + "\",");
        }

        json.append("\"type\":\"" + getType().toString() + "\",");
        if (getWriteObj() != null) {
            json.append("\"write\": " + getWriteObj().toJsonString() + ",");
        }
        json.append("\"read\":" + getReadObj().toJsonString());

        json.append("}");

        return json.toString();
    }

    @NonNull
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("name (M): ");
        builder.append(getDriverName());
        builder.append("\n");

        builder.append("deviceName (M): ");
        builder.append(getDeviceName());
        builder.append("\n");

        builder.append("watchForDeviceName (O): ");
        builder.append(getWatchForDeviceName());
        builder.append("\n");

        builder.append("type (M): ");
        builder.append(getType());
        builder.append("\n");

        builder.append("write (O):\n");
        if (getWriteObj() != null)
            getWriteObj().toStringBuilder(builder, "\t");
        builder.append("\n");

        builder.append("read (M):\n");
        if (getReadObj() != null)
            getReadObj().toStringBuilder(builder, "\t");
        builder.append("\n");

        return builder.toString();
    }

    public class PttWriteObj {
        private List<String> mValidationErrors;

        // Fields
        private UUID mService;
        private UUID mCharacteristic;
        private String mStartCmdStr;
        private DataType mStartCmdStrType;
        private String mEOL;
        private DataType mSerialDataType;
        private IntentMap mIntentMap;

        public PttWriteObj() {
            mValidationErrors = new ArrayList<>();
        }
        public PttWriteObj(JsonReader reader) throws IOException {
            this();
            read(reader);
        }

        public void read(@NonNull JsonReader reader) throws IOException {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();

                Log.v(TAG, "Reading property: " + name);

                switch (name) {
                    case "service":
                        String serviceUUID = reader.nextString();
                        try {
                            mService = UUID.fromString(serviceUUID);
                            Log.v(TAG, mService.toString());
                        } catch (Exception e) {
                            Log.v(TAG, "Failed to parse service UUID: " + serviceUUID);
                        }
                        break;
                    case "characteristic":
                        String characteristicUUID = reader.nextString();
                        try {
                            mCharacteristic = UUID.fromString(characteristicUUID);
                            Log.v(TAG, mCharacteristic.toString());
                        } catch (Exception e) {
                            Log.v(TAG, "Failed to parse characteristic UUID: " + characteristicUUID);
                        }
                        break;
                    case "startCmdStr":
                        mStartCmdStr = reader.nextString();
                        Log.v(TAG, mStartCmdStr);
                        break;
                    case "startCmdStrType":
                        mStartCmdStrType = DataType.toDataType(reader.nextString());
                        Log.v(TAG, mStartCmdStrType.toString());
                        break;
                    case "eol":
                        mEOL = reader.nextString();
                        Log.v(TAG, mEOL);
                        break;
                    case "serialDataType":
                        mSerialDataType = DataType.toDataType(reader.nextString());
                        Log.v(TAG, mSerialDataType.toString());
                        break;
                    case "intentMap":
                        if (reader.peek() != JsonToken.NULL) {
                            Log.v(TAG, "Reading intent map");
                            mIntentMap = readIntentMap(reader);
                        } else {
                            Log.v(TAG, "Could not read intent map");
                        }
                        break;
                    default:
                        Log.d(TAG, "Skipping parameter: " + name);
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
        }

        public List<String> getValidationErrors() {
            return Collections.unmodifiableList(mValidationErrors);
        }

        public boolean isValid() {
            mValidationErrors.clear();

            return true; //getType() != ConnectionType.BLE_SERIAL || (getService() != null && getCharacteristic() != null);
        }

        public UUID getService() { return mService; }
        public UUID getCharacteristic() { return mCharacteristic; }
        public String getStartCmdStr() { return mStartCmdStr; }
        public DataType getStartCmdStrType() { return mStartCmdStrType; }
        public String getEOL() { return mEOL; }
        public DataType getSerialDataType() { return mSerialDataType; }
        public IntentMap getIntentMap() { return mIntentMap; }

        private StringBuilder toStringBuilder(StringBuilder builder, String linePrefix) {
            if (builder == null) {
                builder = new StringBuilder();
            }

            builder.append(linePrefix);
            builder.append("service (O): ");
            builder.append(getService() != null ? getService().toString() : "");
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("characteristic (O): ");
            builder.append(getCharacteristic() != null ? getCharacteristic().toString() : "");
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("startCmdStr (O): ");
            builder.append(getStartCmdStr());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("startCmdStrType (O): ");
            builder.append(getStartCmdStrType());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("eol (M if serialDataType is ascii): ");
            builder.append(getEOL());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("serialDataType (M): ");
            builder.append(getSerialDataType());
            builder.append("\n");

            if (getIntentMap() != null) {
                builder.append(linePrefix);
                builder.append("intentMap (M):\n");
                for (Map.Entry<String, String> mapping : getIntentMap().entrySet()) {
                    builder.append(linePrefix);
                    builder.append("\t");
                    builder.append(mapping.getKey());
                    builder.append(": ");
                    builder.append(mapping.getValue());
                    builder.append("\n");
                }
            }

            return builder;
        }

        public String toJsonString() {
            StringBuilder json = new StringBuilder();
            ArrayList<String> params = new ArrayList<>();

            json.append("{");

            if (getService() != null) {
                params.add("\"service\":\"" + getService().toString() + "\"");
            }
            if (getCharacteristic() != null) {
                params.add("\"characteristic\":\"" + getCharacteristic().toString() + "\"");
            }
            if (getStartCmdStr() != null) {
                params.add("\"startCmdStr\":\"" + getStartCmdStr() + "\"");
            }
            if (getStartCmdStrType() != null) {
                params.add("\"startCmdStrType\":\"" + getStartCmdStrType() + "\"");
            }

            if (getEOL() != null) {
                params.add("\"eol\":\"" + getEOL() + "\"");
            }
            params.add("\"serialDataType\":\"" + getSerialDataType() + "\"");


            if (getIntentMap() != null) {
                ArrayList<String> intentMap = new ArrayList<>();

                for (Map.Entry<String, String> mapping : getIntentMap().entrySet()) {
                    intentMap.add("\"" + mapping.getKey() + "\":\"" + mapping.getValue() + "\"");
                }

                params.add("\"intentMap\":{" + String.join(",", intentMap) + "}");
            }

            json.append(String.join(",", params));

            json.append("}");

            return json.toString();
        }

        @NonNull
        public String toString() {
            return toStringBuilder(new StringBuilder(), "").toString();
        }
    }
    public class PttReadObj {
        private List<String> mValidationErrors;

        // Fields
        private UUID mService;
        private UUID mCharacteristic;
        private String mEOL;
        private DataType mSerialDataType;
        private IntentMap mIntentMap;
        private Map<UUID, IntentMap> mCharacteristicIntentMaps;
        private String mPttDownKeyIntent;
        private int mDefaultPttDownKeyDelay;
        private boolean mDeDupe;
        private int mDeDupeTimeout;
        private List<String> mIntentsDeDuplicateNoTimeout;

        public PttReadObj() {
            mValidationErrors = new ArrayList<>();
            mDefaultPttDownKeyDelay = Device.getPttDownDelayDefault();
            mDeDupe = true;
            mDeDupeTimeout = 50;
            mIntentsDeDuplicateNoTimeout = new ArrayList<>();
            mIntentMap = new IntentMap();
        }
        public PttReadObj(JsonReader reader) throws IOException {
            this();
            read(reader);
        }

        public void read(@NonNull JsonReader reader) throws IOException {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();

                Log.v(TAG, "Reading property: " + name);

                switch (name) {
                    case "service":
                        String serviceUUID = reader.nextString();
                        try {
                            mService = UUID.fromString(serviceUUID);
                            Log.v(TAG, mService.toString());
                        } catch (Exception e) {
                            Log.v(TAG, "Failed to parse service UUID: " + serviceUUID);
                        }
                        break;
                    case "characteristic":
                        String characteristicUUID = reader.nextString();
                        try {
                            mCharacteristic = UUID.fromString(characteristicUUID);
                            Log.v(TAG, mCharacteristic.toString());
                        } catch (Exception e) {
                            Log.v(TAG, "Failed to parse characteristic UUID: " + characteristicUUID);
                        }
                        break;
                    case "pttDownKeyIntent":
                        mPttDownKeyIntent = reader.nextString();
                        Log.v(TAG, mPttDownKeyIntent);
                        break;
                    case "defaultPttDownKeyDelay":
                        mDefaultPttDownKeyDelay = reader.nextInt();
                        Log.v(TAG, "Default ptt key down delay: " + mDefaultPttDownKeyDelay);
                        break;
                    case "intentDeDuplicate":
                        mDeDupe = reader.nextBoolean();
                        Log.v(TAG, "Intent de-dupde: " + mDeDupe);
                        break;
                    case "intentDeDuplicateTimeout":
                        mDeDupeTimeout = reader.nextInt();
                        Log.v(TAG, "Intent de-dupde timeout: " + mDeDupeTimeout + " ms");
                        break;
                    case "intentsDeDuplicateNoTimeout":
                        mIntentsDeDuplicateNoTimeout = readStringList(reader);
                        Log.v(TAG, "Intents de-dupe no timeout: " + mIntentsDeDuplicateNoTimeout);
                        break;
                    case "eol":
                        mEOL = reader.nextString();
                        Log.v(TAG, mEOL);
                        break;
                    case "serialDataType":
                        mSerialDataType = DataType.toDataType(reader.nextString());
                        Log.v(TAG, mSerialDataType.toString());
                        break;
                    case "intentMap":
                        if (reader.peek() != JsonToken.NULL) {
                            Log.v(TAG, "Reading intent map");
                            mIntentMap = readIntentMap(reader);
                        } else {
                            Log.v(TAG, "Could not read intent map");
                        }
                        break;
                    case "characteristicMaps":
                        if (reader.peek() != JsonToken.NULL) {
                            Log.v(TAG, "Reading characteristic intent maps");
                            mCharacteristicIntentMaps = readCharacteristicIntentMaps(reader);
                        } else {
                            Log.v(TAG, "Could not read characteristic intent maps");
                        }
                        break;
                    default:
                        Log.d(TAG, "Skipping parameter: " + name);
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
        }

        private boolean isCharacteristicIntentMapsEmpty() {
            boolean empty = getCharacteristicIntentMaps().isEmpty();

            if (!empty) {
                for (Map.Entry<UUID, IntentMap> mapEntry : getCharacteristicIntentMaps().entrySet()) {
                    if (!empty) {
                        empty = mapEntry.getValue().isEmpty();
                    }
                }
            }

            return empty;
        }

        public List<String> getValidationErrors() {
            return Collections.unmodifiableList(mValidationErrors);
        }

        public boolean isValid() {
            /*boolean typeValid = getType() != ConnectionType.BLE_SERIAL || (getService() != null && getCharacteristic() != null);

            return typeValid &&
                    getSerialDataType() != null && getSerialDataType().isValid() &&
                    (getSerialDataType() != DataType.ASCII || getEOL() != null) &&
                    getIntentMap() != null && !getIntentMap().isEmpty();*/
            boolean valid = true;

            mValidationErrors.clear();

            // All type required fields
            if (getPttDownKeyIntent() == null) {
                valid = false;
                mValidationErrors.add("\'pttKeyDownIntent\' must not be null");
            }

            if (getSerialDataType() == null) {
                valid = false;
                mValidationErrors.add("\'serialDataType\' must not be null");
            } else if (!getSerialDataType().isValid()) {
                valid = false;
                mValidationErrors.add("\'serialDataType\' of \'" + getSerialDataType() + "\' is invalid");
            }

            if (getSerialDataType() == DataType.ASCII && getEOL() == null) {
                valid = false;
                mValidationErrors.add("\'eol\' must not be null for \'serialDataType\' of \'ascii\'");
            }

            // Type specific fields
            switch (getType()) {
                case BLE:
                    if (getService() == null) {
                        valid = false;
                        mValidationErrors.add("\'service\' must not be null");
                    }

                    if (getCharacteristic() != null) {
                        if (getIntentMap() == null) {
                            valid = false;
                            mValidationErrors.add("\'intentMap\' must not be null for type of \'ble\' with \'characteristic\' defined");
                        } else if (getIntentMap().isEmpty()) {
                            valid = false;
                            mValidationErrors.add("\'intentMap\' must not be empty");
                        }
                    } else {
                        if (getCharacteristicIntentMaps() == null) {
                            valid = false;
                            mValidationErrors.add("\'characteristicMaps\' must not be null for type of \'ble\'");
                        } else if (isCharacteristicIntentMapsEmpty()) {
                            valid = false;
                            mValidationErrors.add("\'characteristicMaps\' must not be empty");
                        }
                    }
                    break;
                case BLE_SERIAL:
                    if (getService() == null) {
                        valid = false;
                        mValidationErrors.add("\'service\' must not be null");
                    }

                    if (getCharacteristic() == null) {
                        valid = false;
                        mValidationErrors.add("\'characteristic\' must not be null");
                    }

                    if (getIntentMap() == null) {
                        valid = false;
                        mValidationErrors.add("\'intentMap\' must not be null for type of \'ble-serial\'");
                    } else if (getIntentMap().isEmpty()) {
                        valid = false;
                        mValidationErrors.add("\'intentMap\' must not be empty");
                    }
                    break;
                case SPP:
                case HFP:
                    if (getIntentMap() == null) {
                        valid = false;
                        mValidationErrors.add("\'intentMap\' must not be null for type of \'" + getType() + "\'");
                    } else if (getIntentMap().isEmpty()) {
                        valid = false;
                        mValidationErrors.add("\'intentMap\' must not be empty");
                    }

                    if (getType() == ConnectionType.HFP && getSerialDataType() != DataType.ASCII) {
                        valid = false;
                        mValidationErrors.add("\'serialDataType\' must be \'ascii\' for type of \'hfp\'");
                    }
                    break;
            }

            return valid;
        }

        public UUID getService() { return mService; }
        public UUID getCharacteristic() { return mCharacteristic; }
        public String getPttDownKeyIntent() { return mPttDownKeyIntent; }
        public int getDefaultPttDownKeyDelay() { return mDefaultPttDownKeyDelay; }
        public String getEOL() { return mEOL; }
        public boolean getIntentDeDuplicate() { return mDeDupe; }
        public int getIntentDeDuplicateTimeout() { return mDeDupeTimeout; }
        public List<String> getIntentsDeDuplicateNoTimeout() { return mIntentsDeDuplicateNoTimeout; }
        public DataType getSerialDataType() { return mSerialDataType; }
        public IntentMap getIntentMap() { return mIntentMap; }
        public Map<UUID, IntentMap> getCharacteristicIntentMaps() { return mCharacteristicIntentMaps; }

        private StringBuilder toStringBuilder(StringBuilder builder, String linePrefix) {
            if (builder == null) {
                builder = new StringBuilder();
            }

            builder.append(linePrefix);
            builder.append("service (O for SPP, M for BLE): ");
            builder.append(getService() != null ? getService().toString() : "");
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("characteristic (O for SPP, M for BLE): ");
            builder.append(getCharacteristic() != null ? getCharacteristic().toString() : "");
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("pttDownKeyIntent (M): ");
            builder.append(getPttDownKeyIntent());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("defaultPttDownKeyDelay (O): ");
            builder.append("" + getDefaultPttDownKeyDelay());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("intentDeDuplicate (O - default: true): ");
            builder.append("" + getIntentDeDuplicate());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("intentDeDuplicateTimeout (O - default: 50ms): ");
            builder.append("" + getIntentDeDuplicateTimeout());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("intentsDeDuplicateNoTimeout (O - default: []): ");
            builder.append("" + getIntentsDeDuplicateNoTimeout());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("eol (M if serialDataType is ascii): ");
            builder.append(getEOL());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("serialDataType (M): ");
            builder.append(getSerialDataType());
            builder.append("\n");

            if (getCharacteristicIntentMaps() != null) {
                builder.append(linePrefix);
                builder.append("characteristicMaps (M):\n");
                for (Map.Entry<UUID, IntentMap> mapping : getCharacteristicIntentMaps().entrySet()) {
                    builder.append(linePrefix);
                    builder.append("\t");
                    builder.append(mapping.getKey().toString());
                    builder.append(": ");
                    builder.append("\n");
                    for (Map.Entry<String, String> subMapping : mapping.getValue().entrySet()) {
                        builder.append(linePrefix);
                        builder.append("\t\t");
                        builder.append(subMapping.getKey());
                        builder.append(": ");
                        builder.append(subMapping.getValue());
                        builder.append("\n");
                    }
                }
            } else if (getIntentMap() != null) {
                builder.append(linePrefix);
                builder.append("intentMap (M):\n");
                for (Map.Entry<String, String> mapping : getIntentMap().entrySet()) {
                    builder.append(linePrefix);
                    builder.append("\t");
                    builder.append(mapping.getKey());
                    builder.append(": ");
                    builder.append(mapping.getValue());
                    builder.append("\n");
                }
            }

            return builder;
        }

        public String toJsonString() {
            StringBuilder json = new StringBuilder();
            ArrayList<String> params = new ArrayList<>();

            json.append("{");

            if (getService() != null) {
                params.add("\"service\":\"" + getService().toString() + "\"");
            }
            if (getCharacteristic() != null) {
                params.add("\"characteristic\":\"" + getCharacteristic().toString() + "\"");
            }

            params.add("\"pttDownKeyIntent\":\"" + getPttDownKeyIntent() + "\"");
            params.add("\"defaultPttDownKeyDelay\":" + getDefaultPttDownKeyDelay());
            params.add("\"intentDeDuplicate\":" + getIntentDeDuplicate());
            params.add("\"intentDeDuplicateTimeout\":" + getIntentDeDuplicateTimeout());

            List<String> jsonStringArray = new ArrayList<>();
            for (String value : getIntentsDeDuplicateNoTimeout()) {
                jsonStringArray.add("\"" +  value + "\"");
            }

            params.add("\"intentsDeDuplicateNoTimeout\":[" + String.join(",", jsonStringArray) + "]");
            if (getEOL() != null) {
                params.add("\"eol\":\"" + getEOL() + "\"");
            }
            params.add("\"serialDataType\":\"" + getSerialDataType() + "\"");


            if (getCharacteristicIntentMaps() != null) {
                ArrayList<String> charIntentMaps = new ArrayList<>();

                for (Map.Entry<UUID, IntentMap> mapping : getCharacteristicIntentMaps().entrySet()) {
                    ArrayList<String> intentMap = new ArrayList<>();

                    for (Map.Entry<String, String> subMapping : mapping.getValue().entrySet()) {
                        intentMap.add("\"" + subMapping.getKey() + "\":\"" + subMapping.getValue() + "\"");
                    }

                    charIntentMaps.add("\"" + mapping.getKey() + "\":{" + String.join(",", intentMap) + "}");
                }

                params.add("\"characteristicMaps\":{" + String.join(",", charIntentMaps) + "}");
            } else if (getIntentMap() != null) {
                ArrayList<String> intentMap = new ArrayList<>();

                for (Map.Entry<String, String> mapping : getIntentMap().entrySet()) {
                    intentMap.add("\"" + mapping.getKey() + "\":\"" + mapping.getValue() + "\"");
                }

                params.add("\"intentMap\":{" + String.join(",", intentMap) + "}");
            }

            json.append(String.join(",", params));

            json.append("}");

            return json.toString();
        }

        @NonNull
        public String toString() {
            return toStringBuilder(new StringBuilder(), "").toString();
        }
    }
}
