package com.openmobl.pttDriver.model;

import android.content.Context;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PttDriver implements Validatable {
    private static final String TAG = PttDriver.class.getName();

    public enum ConnectionType {
        INVALID("-", "Invalid"),
        BLE("ble", "BLE"),
        BLE_SERIAL("ble-serial", "BLE Serial"),
        BLE_GAIA("ble-gaia", "BLE GAIA"),
        SPP("spp", "Serial"),
        SPP_GAIA("spp-gaia", "GAIA Serial"),
        HFP("hfp", "Hands-Free"),
        FILESTREAM("filestream", "Local Filestream");

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
    public static class IntentMap extends HashMap<String, String> { }

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

    private static IntentMap readIntentMap(JsonReader reader) throws IOException {
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

    @Override
    public Map<String, List<String>> getAllValidationErrors() {
        HashMap<String, List<String>> results = new HashMap<>();

        results.put("driver", Collections.unmodifiableList(mValidationErrors));
        results.put("read", mReadObj.getValidationErrors());
        if (mWriteObj != null)
            results.put("write", mWriteObj.getValidationErrors());

        return results;
    }

    @Override
    public List<String> getValidationErrors() {
        return null;
    }

    @Override
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
        StringWriter stringOut = new StringWriter();
        JsonWriter writer = new JsonWriter(stringOut);

        toJson(writer);

        return stringOut.toString();
    }
    public JsonWriter toJson(JsonWriter writer) {
        try {
            writer.beginObject();

            writer.name("name").value(getDriverName());
            writer.name("deviceName").value(getDeviceName());

            if (getWatchForDeviceName() != null) {
                writer.name("watchForDeviceName").value(getWatchForDeviceName());
            }

            writer.name("type").value(getType().toString());
            if (getWriteObj() != null) {
                writer.name("write");
                getWriteObj().toJson(writer);
            }
            writer.name("read");
            getReadObj().toJson(writer);

            writer.endObject();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return writer;
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

    public static class PttWriteObj implements Validatable {
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

        @Override
        public Map<String, List<String>> getAllValidationErrors() {
            HashMap<String, List<String>> result = new HashMap<>();

            result.put("PttWriteObj", getValidationErrors());

            return result;
        }

        @Override
        public List<String> getValidationErrors() {
            return Collections.unmodifiableList(mValidationErrors);
        }

        @Override
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
            StringWriter stringOut = new StringWriter();
            JsonWriter writer = new JsonWriter(stringOut);

            toJson(writer);

            return stringOut.toString();
        }
        public JsonWriter toJson(JsonWriter writer) {
            try {
                writer.beginObject();

                if (getService() != null) {
                    writer.name("service").value(getService().toString());
                }
                if (getCharacteristic() != null) {
                    writer.name("characteristic").value(getCharacteristic().toString());
                }
                if (getStartCmdStr() != null) {
                    writer.name("startCmdStr").value(getStartCmdStr());
                }
                if (getStartCmdStrType() != null) {
                    writer.name("startCmdStrType").value(getStartCmdStrType().toString());
                }

                if (getEOL() != null) {
                    writer.name("eol").value(getEOL());
                }
                writer.name("serialDataType").value(getSerialDataType().toString());

                if (getIntentMap() != null) {
                    writer.name("intentMap").beginObject();
                    for (Map.Entry<String, String> mapping : getIntentMap().entrySet()) {
                        writer.name(mapping.getKey()).value(mapping.getValue());
                    }
                    writer.endObject();
                }

                writer.endObject();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return writer;
        }

        @NonNull
        public String toString() {
            return toStringBuilder(new StringBuilder(), "").toString();
        }
    }

    public static class FileObject implements Validatable {
        private List<String> mValidationErrors = new ArrayList<>();

        private String mFilename;
        private String mPreprocessFunc;
        private IntentMap mIntentMap;

        public FileObject(JsonReader reader) throws IOException {
            read(reader);
        }

        public void read(@NonNull JsonReader reader) throws IOException {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();

                switch (name) {
                    case "filename":
                        mFilename = reader.nextString();
                        Log.v(TAG, "Filename: " + mFilename);
                        break;
                    case "preprocess":
                        mPreprocessFunc = reader.nextString();
                        Log.v(TAG, "Filename: " + mPreprocessFunc);
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

        public String getFileName() {
            return mFilename;
        }

        public String getPreprocessFunction() {
            return mPreprocessFunc;
        }

        public IntentMap getIntentMap() {
            return mIntentMap;
        }

        @Override
        public Map<String, List<String>> getAllValidationErrors() {
            HashMap<String, List<String>> result = new HashMap<>();

            result.put("FileObject", getValidationErrors());

            return result;
        }

        @Override
        public List<String> getValidationErrors() {
            return null;
        }

        @Override
        public boolean isValid() {
            boolean valid = true;

            mValidationErrors.clear();

            if (getFileName() == null) {
                valid = false;
                mValidationErrors.add("\'filename\' must not be null for a file object");
            } else if (getFileName().isEmpty()) {
                valid = false;
                mValidationErrors.add("\'filename\' must not be empty");
            }

            if (getPreprocessFunction() == null) {
                valid = false;
                mValidationErrors.add("\'preprocess\' must not be null for a file object");
            } else if (getPreprocessFunction().isEmpty()) {
                valid = false;
                mValidationErrors.add("\'preprocess\' must not be empty");
            }

            if (getIntentMap() == null) {
                valid = false;
                mValidationErrors.add("\'intentMap\' must not be null for a file object");
            } else if (getIntentMap().isEmpty()) {
                valid = false;
                mValidationErrors.add("\'intentMap\' must not be empty");
            }

            return valid;
        }

        private StringBuilder toStringBuilder(StringBuilder builder, String linePrefix) {
            if (builder == null) {
                builder = new StringBuilder();
            }

            builder.append(linePrefix);
            builder.append("filename (M): ");
            builder.append(getFileName());
            builder.append("\n");

            builder.append(linePrefix);
            builder.append("preprocess (M): ");
            builder.append(getPreprocessFunction());
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
            StringWriter stringOut = new StringWriter();
            JsonWriter writer = new JsonWriter(stringOut);

            toJson(writer);

            return stringOut.toString();
        }
        public JsonWriter toJson(JsonWriter writer) {
            try {
                writer.beginObject();

                if (getFileName() != null) {
                    writer.name("filename").value(getFileName());
                }
                if (getPreprocessFunction() != null) {
                    writer.name("preprocess").value(getPreprocessFunction());
                }

                if (getIntentMap() != null) {
                    writer.name("intentMap").beginObject();
                    for (Map.Entry<String, String> mapping : getIntentMap().entrySet()) {
                        writer.name(mapping.getKey()).value(mapping.getValue());
                    }
                    writer.endObject();
                }

                writer.endObject();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return writer;
        }

        @NonNull
        public String toString() {
            return toStringBuilder(new StringBuilder(), "").toString();
        }
    }

    public class PttReadObj implements Validatable {
        public class OperationsMap extends HashMap<String, String> { }

        private List<String> mValidationErrors;

        // Fields
        private String mPttDownKeyIntent;
        private int mDefaultPttDownKeyDelay;
        private DataType mSerialDataType;
        private String mEOL;
        private UUID mService;
        private UUID mCharacteristic;
        private IntentMap mIntentMap;
        private Map<UUID, IntentMap> mCharacteristicIntentMaps;
        private boolean mDeDupe;
        private int mDeDupeTimeout;
        private List<String> mIntentsDeDuplicateNoTimeout;
        private OperationsMap mOperationsMap;
        private List<FileObject> mFiles;

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
                        Log.v(TAG, "EOL: " + mEOL);
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
                    case "operations":
                        if (reader.peek() != JsonToken.NULL) {
                            Log.v(TAG, "Reading operations map");
                            mOperationsMap = readOperationsMap(reader);
                        } else {
                            Log.v(TAG, "Could not read operations map");
                        }
                        break;
                    case "files":
                        if (reader.peek() != JsonToken.NULL) {
                            Log.v(TAG, "Reading files list");
                            mFiles = readFilesList(reader);
                        } else {
                            Log.v(TAG, "Could not read files list");
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

        private OperationsMap readOperationsMap(JsonReader reader) throws IOException {
            OperationsMap opsMap = new OperationsMap();

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                String value = reader.nextString();

                Log.v(TAG, "Operations mapping: " + name + " -> " + value);

                opsMap.put(name, value);
            }
            reader.endObject();

            return opsMap;
        }

        private List<FileObject> readFilesList(JsonReader reader) throws IOException {
            ArrayList<FileObject> files = new ArrayList<>();

            reader.beginArray();
            while (reader.hasNext()) {
                files.add(new FileObject(reader));
            }
            reader.endArray();

            return files;
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

        @Override
        public Map<String, List<String>> getAllValidationErrors() {
            HashMap<String, List<String>> result = new HashMap<>();

            result.put("PttReadObj", getValidationErrors());

            return result;
        }

        @Override
        public List<String> getValidationErrors() {
            return Collections.unmodifiableList(mValidationErrors);
        }

        @Override
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
                case FILESTREAM:
                    if (getOperationsMap() == null) {
                        valid = false;
                        mValidationErrors.add("\'operations\' must not be null for type of \'" + getType() + "\'");
                    } else if (getOperationsMap().isEmpty()){
                        valid = false;
                        mValidationErrors.add("\'operations\' must not be empty");
                    }

                    if (getFiles() == null) {
                        valid = false;
                        mValidationErrors.add("\'files\' must not be null for type of \'" + getType() + "\'");
                    } else if (getFiles().size() == 0) {
                        valid = false;
                        mValidationErrors.add("\'files\' must not be empty");
                    } else {
                        for (FileObject file: getFiles()) {
                            if (!file.isValid()) {
                                valid = false;
                                mValidationErrors.addAll(file.getValidationErrors());
                            }
                        }
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
        public OperationsMap getOperationsMap() { return mOperationsMap; }
        public List<FileObject> getFiles() { return mFiles; }

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

            builder.append(linePrefix);
            builder.append("characteristicMaps ():\n");
            if (getCharacteristicIntentMaps() != null) {
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
            } else {
                builder.append("null\n");
                builder.append("\n");
            }

            builder.append(linePrefix);
            builder.append("intentMap ():\n");
            if (getIntentMap() != null) {
                for (Map.Entry<String, String> mapping : getIntentMap().entrySet()) {
                    builder.append(linePrefix);
                    builder.append("\t");
                    builder.append(mapping.getKey());
                    builder.append(": ");
                    builder.append(mapping.getValue());
                    builder.append("\n");
                }
            } else {
                builder.append("null\n");
                builder.append("\n");
            }

            builder.append(linePrefix);
            builder.append("operations (M for FILESTREAM): ");
            if (getOperationsMap() != null) {
                for (Map.Entry<String, String> operation: getOperationsMap().entrySet()) {
                    builder.append(linePrefix);
                    builder.append("\t");
                    builder.append(operation.getKey());
                    builder.append(": '");
                    builder.append(operation.getValue());
                    builder.append("'\n");
                }
            } else {
                builder.append("null\n");
                builder.append("\n");
            }

            builder.append(linePrefix);
            builder.append("files (M for FILESTREAM): ");
            if (getFiles() != null) {
                for (FileObject file: getFiles()) {
                    builder.append(file.toStringBuilder(builder, linePrefix));
                }
            } else {
                builder.append("null\n");
                builder.append("\n");
            }

            return builder;
        }

        public String toJsonString() {
            StringWriter stringOut = new StringWriter();
            JsonWriter writer = new JsonWriter(stringOut);

            toJson(writer);

            return stringOut.toString();
        }
        public JsonWriter toJson(JsonWriter writer) {
            try {
                writer.beginObject();

                if (getService() != null) {
                    writer.name("service").value(getService().toString());
                }
                if (getCharacteristic() != null) {
                    writer.name("characteristic").value(getCharacteristic().toString());
                }

                writer.name("pttDownKeyIntent").value(getPttDownKeyIntent());
                writer.name("defaultPttDownKeyDelay").value(getDefaultPttDownKeyDelay());
                writer.name("intentDeDuplicate").value(getIntentDeDuplicate());
                writer.name("intentDeDuplicateTimeout").value(getIntentDeDuplicateTimeout());

                writer.name("intentsDeDuplicateNoTimeout").beginArray();
                for (String value : getIntentsDeDuplicateNoTimeout()) {
                    writer.value(value);
                }
                writer.endArray();

                if (getEOL() != null) {
                    writer.name("eol").value(getEOL());
                }
                writer.name("serialDataType").value(getSerialDataType().toString());

                if (getCharacteristicIntentMaps() != null) {
                    writer.name("characteristicMaps").beginObject();
                    for (Map.Entry<UUID, IntentMap> mapping : getCharacteristicIntentMaps().entrySet()) {
                        writer.name(mapping.getKey().toString()).beginObject();
                        for (Map.Entry<String, String> subMapping : mapping.getValue().entrySet()) {
                            writer.name(subMapping.getKey()).value(subMapping.getValue());
                        }
                        writer.endObject();
                    }
                    writer.endObject();
                } else if (getIntentMap() != null) {
                    writer.name("intentMap").beginObject();
                    for (Map.Entry<String, String> mapping : getIntentMap().entrySet()) {
                        writer.name(mapping.getKey()).value(mapping.getValue());
                    }
                    writer.endObject();
                }

                if (getOperationsMap() != null) {
                    writer.name("operations").beginObject();
                    for (Map.Entry<String, String> mapping : getOperationsMap().entrySet()) {
                        writer.name(mapping.getKey()).value(mapping.getValue());
                    }
                    writer.endObject();
                }

                if (getFiles() != null) {
                    writer.name("files").beginArray();
                    for (FileObject file: getFiles()) {
                        file.toJson(writer);
                    }
                    writer.endArray();
                }

                writer.endObject();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return writer;
        }

        @NonNull
        public String toString() {
            return toStringBuilder(new StringBuilder(), "").toString();
        }
    }

    public static String escapeJsonString(String value) {
        try {
            Writer out = new StringWriter();

            for (int i = 0, length = value.length(); i < length; i++) {
                char c = value.charAt(i);
                /*
                 * From RFC 4627, "All Unicode characters may be placed within the
                 * quotation marks except for the characters that must be escaped:
                 * quotation mark, reverse solidus, and the control characters
                 * (U+0000 through U+001F)."
                 *
                 * We also escape '\u2028' and '\u2029', which JavaScript interprets
                 * as newline characters. This prevents eval() from failing with a
                 * syntax error.
                 * http://code.google.com/p/google-gson/issues/detail?id=341
                 */
                switch (c) {
                    case '"':
                    case '\\':
                        out.write('\\');
                        out.write(c);
                        break;
                    case '\t':
                        out.write("\\t");
                        break;
                    case '\b':
                        out.write("\\b");
                        break;
                    case '\n':
                        out.write("\\n");
                        break;
                    case '\r':
                        out.write("\\r");
                        break;
                    case '\f':
                        out.write("\\f");
                        break;
                    case '\u2028':
                    case '\u2029':
                        out.write(String.format("\\u%04x", (int) c));
                        break;
                    default:
                        if (c <= 0x1F) {
                            out.write(String.format("\\u%04x", (int) c));
                        } else {
                            out.write(c);
                        }
                        break;
                }
            }

            String result = out.toString();

            Log.v(TAG, "escaped: " + result);

            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
