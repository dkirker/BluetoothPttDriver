package com.openmobl.pttDriver.model

import android.content.*
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import java.io.*
import java.util.*

class PttDriver() : Validatable {
    enum class ConnectionType(private val mConnectionType: String, private val mTypeName: String) {
        INVALID("-", "Invalid"), BLE("ble", "BLE"), BLE_SERIAL(
            "ble-serial",
            "BLE Serial"
        ),
        BLE_GAIA("ble-gaia", "BLE GAIA"), SPP("spp", "Serial"), SPP_GAIA(
            "spp-gaia",
            "GAIA Serial"
        ),
        HFP("hfp", "Hands-Free"), FILESTREAM("filestream", "Local Filestream");

        val isValid: Boolean
            get() = this != INVALID

        override fun toString(): String {
            return mConnectionType
        }

        fun toHumanReadableString(): String {
            return mTypeName
        }

        companion object {
            fun toConnectionType(value: String): ConnectionType {
                for (connType in values()) {
                    if (value == connType.toString()) return connType
                }
                return INVALID
            }
        }
    }

    enum class DataType(private val mDataType: String) {
        INVALID("-"), ASCII("ascii"), HEX("hex");

        val isValid: Boolean
            get() = this != INVALID

        override fun toString(): String {
            return mDataType
        }

        companion object {
            fun toDataType(value: String): DataType {
                for (dataTypeEnum in values()) {
                    if (value == dataTypeEnum.toString()) return dataTypeEnum
                }
                return INVALID
            }
        }
    }

    class IntentMap : HashMap<String?, String?>()

    private var mReader: JsonReader? = null
    private val mValidationErrors: MutableList<String>

    // Fields
    var driverName: String? = null
        private set
    var deviceName: String? = null
        private set
    var watchForDeviceName: String? = null
        private set
    var type: ConnectionType? = null
        private set
    var writeObj: PttWriteObj? = null
        private set
    var readObj: PttReadObj? = null
        private set

    init {
        mValidationErrors = ArrayList()
    }

    constructor(context: Context?, content: Uri?) : this() {
        read(context!!, content)
    }

    constructor(context: Context, json: String?) : this() {
        read(context, json)
    }

    constructor(reader: JsonReader) : this() {
        read(reader)
    }

    @Throws(IOException::class)
    fun read(context: Context, content: Uri?) {
        val inputStream = context.contentResolver.openInputStream(content!!)
        if (inputStream != null) {
            val streamReader = InputStreamReader(inputStream, "UTF-8")
            val reader = JsonReader(streamReader)
            read(reader)
        } else {
            Log.d(TAG, "Failed to open InputStream for $content")
            throw IOException("Failed to open InputStream for $content")
        }
    }

    @Throws(IOException::class)
    fun read(context: Context, json: String?) {
        val inputStream: InputStream = ByteArrayInputStream(json!!.toByteArray())
        if (inputStream != null) {
            val streamReader = InputStreamReader(inputStream, "UTF-8")
            val reader = JsonReader(streamReader)
            read(reader)
        } else {
            Log.d(TAG, "Failed to open InputStream for JSON String")
            throw IOException("Failed to open InputStream for JSON String")
        }
    }

    @Throws(IOException::class)
    fun read(reader: JsonReader) {
        mReader = reader
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            Log.v(TAG, "Reading property: $name")
            when (name) {
                "name" -> {
                    driverName = reader.nextString()
                    Log.v(TAG, driverName)
                }
                "deviceName" -> {
                    deviceName = reader.nextString()
                    Log.v(TAG, deviceName)
                }
                "watchForDeviceName" -> {
                    watchForDeviceName = reader.nextString()
                    Log.v(TAG, watchForDeviceName)
                }
                "type" -> {
                    type = ConnectionType.toConnectionType(reader.nextString())
                    Log.v(TAG, type.toString())
                }
                "write" -> if (reader.peek() != JsonToken.NULL) {
                    Log.v(TAG, "Creating PttWriteObj")
                    writeObj = PttWriteObj(reader)
                } else {
                    Log.v(TAG, "write tag was null")
                }
                "read" -> if (reader.peek() != JsonToken.NULL) {
                    Log.v(TAG, "Creating PttReadObj")
                    readObj = PttReadObj(reader)
                } else {
                    Log.v(TAG, "read tag was null")
                }
                else -> {
                    Log.d(TAG, "Skipping parameter: $name")
                    reader.skipValue()
                }
            }
        }
        reader.endObject()
    }

    @Throws(IOException::class)
    private fun readStringList(reader: JsonReader): List<String> {
        val stringList: MutableList<String> = ArrayList()
        reader.beginArray()
        while (reader.hasNext()) {
            val value = reader.nextString()
            Log.v(TAG, "Adding intent to de-dupe list: $value")
            stringList.add(value)
        }
        reader.endArray()
        return stringList
    }

    @Throws(IOException::class)
    private fun readCharacteristicIntentMaps(reader: JsonReader): Map<UUID, IntentMap?> {
        val intentMap: MutableMap<UUID, IntentMap?> = HashMap()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            var value: IntentMap? = null
            if (reader.peek() != JsonToken.NULL) {
                value = readIntentMap(reader)
            }
            try {
                intentMap[UUID.fromString(name)] = value
            } catch (e: Exception) {
                Log.v(TAG, "Could not insert intent map: $e")
            }
        }
        reader.endObject()
        return intentMap
    }

    override val allValidationErrors: Map<String, List<String>?>
        get() {
            val results = HashMap<String, List<String>?>()
            results["driver"] = Collections.unmodifiableList(mValidationErrors)
            results["read"] = readObj!!.validationErrors
            if (writeObj != null) results["write"] = writeObj!!.validationErrors
            return results
        }
    override val validationErrors: List<String>?
        get() = null
    override val isValid: Boolean
        get() {
            var valid = true
            mValidationErrors.clear()
            if (driverName == null) {
                valid = false
                mValidationErrors.add("\'driverName\' must not be null")
            }
            if (deviceName == null) {
                valid = false
                mValidationErrors.add("\'deviceName\' must not be null")
            }
            if (type == null) {
                valid = false
                mValidationErrors.add("\'type\' must not be null")
            } else if (!type!!.isValid) {
                valid = false
                mValidationErrors.add("\'driverName\' must not be null")
            }
            if (writeObj != null && !writeObj!!.isValid) {
                valid = false
                mValidationErrors.add("\'write\' is not valid")
            }
            if (readObj == null) {
                valid = false
                mValidationErrors.add("\'read\' must not be null")
            } else if (!readObj!!.isValid) {
                valid = false
                mValidationErrors.add("\'read\' is not valid")
            }
            return valid
        }

    fun toJsonString(): String {
        val stringOut = StringWriter()
        val writer = JsonWriter(stringOut)
        toJson(writer)
        return stringOut.toString()
    }

    fun toJson(writer: JsonWriter): JsonWriter {
        try {
            writer.beginObject()
            writer.name("name").value(driverName)
            writer.name("deviceName").value(deviceName)
            if (watchForDeviceName != null) {
                writer.name("watchForDeviceName").value(watchForDeviceName)
            }
            writer.name("type").value(type.toString())
            if (writeObj != null) {
                writer.name("write")
                writeObj!!.toJson(writer)
            }
            writer.name("read")
            readObj!!.toJson(writer)
            writer.endObject()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return writer
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("name (M): ")
        builder.append(driverName)
        builder.append("\n")
        builder.append("deviceName (M): ")
        builder.append(deviceName)
        builder.append("\n")
        builder.append("watchForDeviceName (O): ")
        builder.append(watchForDeviceName)
        builder.append("\n")
        builder.append("type (M): ")
        builder.append(type)
        builder.append("\n")
        builder.append("write (O):\n")
        if (writeObj != null) writeObj!!.toStringBuilder(builder, "\t")
        builder.append("\n")
        builder.append("read (M):\n")
        if (readObj != null) readObj!!.toStringBuilder(builder, "\t")
        builder.append("\n")
        return builder.toString()
    }

    class PttWriteObj() : Validatable {
        private val mValidationErrors: MutableList<String>

        // Fields
        var service: UUID? = null
            private set
        var characteristic: UUID? = null
            private set
        var startCmdStr: String? = null
            private set
        var startCmdStrType: DataType? = null
            private set
        var eOL: String? = null
            private set
        var serialDataType: DataType? = null
            private set
        var intentMap: IntentMap? = null
            private set

        init {
            mValidationErrors = ArrayList()
        }

        constructor(reader: JsonReader) : this() {
            read(reader)
        }

        @Throws(IOException::class)
        fun read(reader: JsonReader) {
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                Log.v(TAG, "Reading property: $name")
                when (name) {
                    "service" -> {
                        val serviceUUID = reader.nextString()
                        try {
                            service = UUID.fromString(serviceUUID)
                            Log.v(TAG, service.toString())
                        } catch (e: Exception) {
                            Log.v(TAG, "Failed to parse service UUID: $serviceUUID")
                        }
                    }
                    "characteristic" -> {
                        val characteristicUUID = reader.nextString()
                        try {
                            characteristic = UUID.fromString(characteristicUUID)
                            Log.v(TAG, characteristic.toString())
                        } catch (e: Exception) {
                            Log.v(TAG, "Failed to parse characteristic UUID: $characteristicUUID")
                        }
                    }
                    "startCmdStr" -> {
                        startCmdStr = reader.nextString()
                        Log.v(TAG, startCmdStr)
                    }
                    "startCmdStrType" -> {
                        startCmdStrType = DataType.toDataType(reader.nextString())
                        Log.v(TAG, startCmdStrType.toString())
                    }
                    "eol" -> {
                        eOL = reader.nextString()
                        Log.v(TAG, eOL)
                    }
                    "serialDataType" -> {
                        serialDataType = DataType.toDataType(reader.nextString())
                        Log.v(TAG, serialDataType.toString())
                    }
                    "intentMap" -> if (reader.peek() != JsonToken.NULL) {
                        Log.v(TAG, "Reading intent map")
                        intentMap = readIntentMap(reader)
                    } else {
                        Log.v(TAG, "Could not read intent map")
                    }
                    else -> {
                        Log.d(TAG, "Skipping parameter: $name")
                        reader.skipValue()
                    }
                }
            }
            reader.endObject()
        }

        override fun getAllValidationErrors(): Map<String, List<String>?> {
            val result = HashMap<String, List<String>?>()
            result["PttWriteObj"] = validationErrors
            return result
        }

        override fun getValidationErrors(): List<String>? {
            return Collections.unmodifiableList(mValidationErrors)
        }

        override fun isValid(): Boolean {
            mValidationErrors.clear()
            return true //getType() != ConnectionType.BLE_SERIAL || (getService() != null && getCharacteristic() != null);
        }

        fun toStringBuilder(builder: StringBuilder, linePrefix: String): StringBuilder {
            var builder: StringBuilder? = builder
            if (builder == null) {
                builder = StringBuilder()
            }
            builder.append(linePrefix)
            builder.append("service (O): ")
            builder.append(if (service != null) service.toString() else "")
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("characteristic (O): ")
            builder.append(if (characteristic != null) characteristic.toString() else "")
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("startCmdStr (O): ")
            builder.append(startCmdStr)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("startCmdStrType (O): ")
            builder.append(startCmdStrType)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("eol (M if serialDataType is ascii): ")
            builder.append(eOL)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("serialDataType (M): ")
            builder.append(serialDataType)
            builder.append("\n")
            if (intentMap != null) {
                builder.append(linePrefix)
                builder.append("intentMap (M):\n")
                for ((key, value) in intentMap!!) {
                    builder.append(linePrefix)
                    builder.append("\t")
                    builder.append(key)
                    builder.append(": ")
                    builder.append(value)
                    builder.append("\n")
                }
            }
            return builder
        }

        fun toJsonString(): String {
            val stringOut = StringWriter()
            val writer = JsonWriter(stringOut)
            toJson(writer)
            return stringOut.toString()
        }

        fun toJson(writer: JsonWriter): JsonWriter {
            try {
                writer.beginObject()
                if (service != null) {
                    writer.name("service").value(service.toString())
                }
                if (characteristic != null) {
                    writer.name("characteristic").value(characteristic.toString())
                }
                if (startCmdStr != null) {
                    writer.name("startCmdStr").value(startCmdStr)
                }
                if (startCmdStrType != null) {
                    writer.name("startCmdStrType").value(startCmdStrType.toString())
                }
                if (eOL != null) {
                    writer.name("eol").value(eOL)
                }
                writer.name("serialDataType").value(serialDataType.toString())
                if (intentMap != null) {
                    writer.name("intentMap").beginObject()
                    for ((key, value) in intentMap!!) {
                        writer.name(key).value(value)
                    }
                    writer.endObject()
                }
                writer.endObject()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return writer
        }

        override fun toString(): String {
            return toStringBuilder(StringBuilder(), "").toString()
        }
    }

    class FileObject(reader: JsonReader) : Validatable {
        private val mValidationErrors: MutableList<String> = ArrayList()
        var fileName: String? = null
            private set
        var preprocessFunction: String? = null
            private set
        var intentMap: IntentMap? = null
            private set

        init {
            read(reader)
        }

        @Throws(IOException::class)
        fun read(reader: JsonReader) {
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                when (name) {
                    "filename" -> {
                        fileName = reader.nextString()
                        Log.v(TAG, "Filename: " + fileName)
                    }
                    "preprocess" -> {
                        preprocessFunction = reader.nextString()
                        Log.v(TAG, "Filename: " + preprocessFunction)
                    }
                    "intentMap" -> if (reader.peek() != JsonToken.NULL) {
                        Log.v(TAG, "Reading intent map")
                        intentMap = readIntentMap(reader)
                    } else {
                        Log.v(TAG, "Could not read intent map")
                    }
                    else -> {
                        Log.d(TAG, "Skipping parameter: $name")
                        reader.skipValue()
                    }
                }
            }
            reader.endObject()
        }

        override fun getAllValidationErrors(): Map<String, List<String>?> {
            val result = HashMap<String, List<String>?>()
            result["FileObject"] = validationErrors
            return result
        }

        override fun getValidationErrors(): List<String>? {
            return null
        }

        override fun isValid(): Boolean {
            var valid = true
            mValidationErrors.clear()
            if (fileName == null) {
                valid = false
                mValidationErrors.add("\'filename\' must not be null for a file object")
            } else if (fileName!!.isEmpty()) {
                valid = false
                mValidationErrors.add("\'filename\' must not be empty")
            }
            if (preprocessFunction == null) {
                valid = false
                mValidationErrors.add("\'preprocess\' must not be null for a file object")
            } else if (preprocessFunction!!.isEmpty()) {
                valid = false
                mValidationErrors.add("\'preprocess\' must not be empty")
            }
            if (intentMap == null) {
                valid = false
                mValidationErrors.add("\'intentMap\' must not be null for a file object")
            } else if (intentMap!!.isEmpty()) {
                valid = false
                mValidationErrors.add("\'intentMap\' must not be empty")
            }
            return valid
        }

        fun toStringBuilder(builder: StringBuilder, linePrefix: String): StringBuilder {
            var builder: StringBuilder? = builder
            if (builder == null) {
                builder = StringBuilder()
            }
            builder.append(linePrefix)
            builder.append("filename (M): ")
            builder.append(fileName)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("preprocess (M): ")
            builder.append(preprocessFunction)
            builder.append("\n")
            if (intentMap != null) {
                builder.append(linePrefix)
                builder.append("intentMap (M):\n")
                for ((key, value) in intentMap!!) {
                    builder.append(linePrefix)
                    builder.append("\t")
                    builder.append(key)
                    builder.append(": ")
                    builder.append(value)
                    builder.append("\n")
                }
            }
            return builder
        }

        fun toJsonString(): String {
            val stringOut = StringWriter()
            val writer = JsonWriter(stringOut)
            toJson(writer)
            return stringOut.toString()
        }

        fun toJson(writer: JsonWriter): JsonWriter {
            try {
                writer.beginObject()
                if (fileName != null) {
                    writer.name("filename").value(fileName)
                }
                if (preprocessFunction != null) {
                    writer.name("preprocess").value(preprocessFunction)
                }
                if (intentMap != null) {
                    writer.name("intentMap").beginObject()
                    for ((key, value) in intentMap!!) {
                        writer.name(key).value(value)
                    }
                    writer.endObject()
                }
                writer.endObject()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return writer
        }

        override fun toString(): String {
            return toStringBuilder(StringBuilder(), "").toString()
        }
    }

    inner class PttReadObj() : Validatable {
        inner class OperationsMap : HashMap<String?, String?>()

        private val mValidationErrors: MutableList<String>

        // Fields
        var pttDownKeyIntent: String? = null
            private set
        var defaultPttDownKeyDelay: Int
            private set
        var serialDataType: DataType? = null
            private set
        var eOL: String? = null
            private set
        var service: UUID? = null
            private set
        var characteristic: UUID? = null
            private set
        private var mIntentMap: IntentMap
        var characteristicIntentMaps: Map<UUID, IntentMap?>? = null
            private set
        var intentDeDuplicate: Boolean
            private set
        var intentDeDuplicateTimeout: Int
            private set
        var intentsDeDuplicateNoTimeout: List<String>
            private set
        var operationsMap: OperationsMap? = null
            private set
        var files: List<FileObject>? = null
            private set

        init {
            mValidationErrors = ArrayList()
            defaultPttDownKeyDelay = Device.Companion.getPttDownDelayDefault()
            intentDeDuplicate = true
            intentDeDuplicateTimeout = 50
            intentsDeDuplicateNoTimeout = ArrayList()
            mIntentMap = IntentMap()
        }

        constructor(reader: JsonReader) : this() {
            read(reader)
        }

        @Throws(IOException::class)
        fun read(reader: JsonReader) {
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                Log.v(TAG, "Reading property: $name")
                when (name) {
                    "service" -> {
                        val serviceUUID = reader.nextString()
                        try {
                            service = UUID.fromString(serviceUUID)
                            Log.v(TAG, service.toString())
                        } catch (e: Exception) {
                            Log.v(TAG, "Failed to parse service UUID: $serviceUUID")
                        }
                    }
                    "characteristic" -> {
                        val characteristicUUID = reader.nextString()
                        try {
                            characteristic = UUID.fromString(characteristicUUID)
                            Log.v(TAG, characteristic.toString())
                        } catch (e: Exception) {
                            Log.v(TAG, "Failed to parse characteristic UUID: $characteristicUUID")
                        }
                    }
                    "pttDownKeyIntent" -> {
                        pttDownKeyIntent = reader.nextString()
                        Log.v(TAG, pttDownKeyIntent)
                    }
                    "defaultPttDownKeyDelay" -> {
                        defaultPttDownKeyDelay = reader.nextInt()
                        Log.v(TAG, "Default ptt key down delay: " + defaultPttDownKeyDelay)
                    }
                    "intentDeDuplicate" -> {
                        intentDeDuplicate = reader.nextBoolean()
                        Log.v(TAG, "Intent de-dupde: " + intentDeDuplicate)
                    }
                    "intentDeDuplicateTimeout" -> {
                        intentDeDuplicateTimeout = reader.nextInt()
                        Log.v(TAG, "Intent de-dupde timeout: " + intentDeDuplicateTimeout + " ms")
                    }
                    "intentsDeDuplicateNoTimeout" -> {
                        intentsDeDuplicateNoTimeout = readStringList(reader)
                        Log.v(TAG, "Intents de-dupe no timeout: " + intentsDeDuplicateNoTimeout)
                    }
                    "eol" -> {
                        eOL = reader.nextString()
                        Log.v(TAG, "EOL: " + eOL)
                    }
                    "serialDataType" -> {
                        serialDataType = DataType.toDataType(reader.nextString())
                        Log.v(TAG, serialDataType.toString())
                    }
                    "intentMap" -> if (reader.peek() != JsonToken.NULL) {
                        Log.v(TAG, "Reading intent map")
                        mIntentMap = readIntentMap(reader)
                    } else {
                        Log.v(TAG, "Could not read intent map")
                    }
                    "characteristicMaps" -> if (reader.peek() != JsonToken.NULL) {
                        Log.v(TAG, "Reading characteristic intent maps")
                        characteristicIntentMaps = readCharacteristicIntentMaps(reader)
                    } else {
                        Log.v(TAG, "Could not read characteristic intent maps")
                    }
                    "operations" -> if (reader.peek() != JsonToken.NULL) {
                        Log.v(TAG, "Reading operations map")
                        operationsMap = readOperationsMap(reader)
                    } else {
                        Log.v(TAG, "Could not read operations map")
                    }
                    "files" -> if (reader.peek() != JsonToken.NULL) {
                        Log.v(TAG, "Reading files list")
                        files = readFilesList(reader)
                    } else {
                        Log.v(TAG, "Could not read files list")
                    }
                    else -> {
                        Log.d(TAG, "Skipping parameter: $name")
                        reader.skipValue()
                    }
                }
            }
            reader.endObject()
        }

        @Throws(IOException::class)
        private fun readOperationsMap(reader: JsonReader): OperationsMap {
            val opsMap = OperationsMap()
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                val value = reader.nextString()
                Log.v(TAG, "Operations mapping: $name -> $value")
                opsMap[name] = value
            }
            reader.endObject()
            return opsMap
        }

        @Throws(IOException::class)
        private fun readFilesList(reader: JsonReader): List<FileObject> {
            val files = ArrayList<FileObject>()
            reader.beginArray()
            while (reader.hasNext()) {
                files.add(FileObject(reader))
            }
            reader.endArray()
            return files
        }

        private val isCharacteristicIntentMapsEmpty: Boolean
            private get() {
                var empty = characteristicIntentMaps!!.isEmpty()
                if (!empty) {
                    for ((_, value) in characteristicIntentMaps!!) {
                        if (!empty) {
                            empty = value!!.isEmpty()
                        }
                    }
                }
                return empty
            }

        override fun getAllValidationErrors(): Map<String, List<String>?> {
            val result = HashMap<String, List<String>?>()
            result["PttReadObj"] = validationErrors
            return result
        }

        override fun getValidationErrors(): List<String>? {
            return Collections.unmodifiableList(mValidationErrors)
        }

        override fun isValid(): Boolean {
            /*boolean typeValid = getType() != ConnectionType.BLE_SERIAL || (getService() != null && getCharacteristic() != null);

            return typeValid &&
                    getSerialDataType() != null && getSerialDataType().isValid() &&
                    (getSerialDataType() != DataType.ASCII || getEOL() != null) &&
                    getIntentMap() != null && !getIntentMap().isEmpty();*/
            var valid = true
            mValidationErrors.clear()

            // All type required fields
            if (pttDownKeyIntent == null) {
                valid = false
                mValidationErrors.add("\'pttKeyDownIntent\' must not be null")
            }
            if (serialDataType == null) {
                valid = false
                mValidationErrors.add("\'serialDataType\' must not be null")
            } else if (!serialDataType!!.isValid) {
                valid = false
                mValidationErrors.add("\'serialDataType\' of \'" + serialDataType + "\' is invalid")
            }
            if (serialDataType == DataType.ASCII && eOL == null) {
                valid = false
                mValidationErrors.add("\'eol\' must not be null for \'serialDataType\' of \'ascii\'")
            }
            when (type) {
                ConnectionType.BLE -> {
                    if (service == null) {
                        valid = false
                        mValidationErrors.add("\'service\' must not be null")
                    }
                    if (characteristic != null) {
                        if (intentMap == null) {
                            valid = false
                            mValidationErrors.add("\'intentMap\' must not be null for type of \'ble\' with \'characteristic\' defined")
                        } else if (intentMap!!.isEmpty()) {
                            valid = false
                            mValidationErrors.add("\'intentMap\' must not be empty")
                        }
                    } else {
                        if (characteristicIntentMaps == null) {
                            valid = false
                            mValidationErrors.add("\'characteristicMaps\' must not be null for type of \'ble\'")
                        } else if (isCharacteristicIntentMapsEmpty) {
                            valid = false
                            mValidationErrors.add("\'characteristicMaps\' must not be empty")
                        }
                    }
                }
                ConnectionType.BLE_SERIAL -> {
                    if (service == null) {
                        valid = false
                        mValidationErrors.add("\'service\' must not be null")
                    }
                    if (characteristic == null) {
                        valid = false
                        mValidationErrors.add("\'characteristic\' must not be null")
                    }
                    if (intentMap == null) {
                        valid = false
                        mValidationErrors.add("\'intentMap\' must not be null for type of \'ble-serial\'")
                    } else if (intentMap!!.isEmpty()) {
                        valid = false
                        mValidationErrors.add("\'intentMap\' must not be empty")
                    }
                }
                ConnectionType.SPP, ConnectionType.HFP -> {
                    if (intentMap == null) {
                        valid = false
                        mValidationErrors.add("\'intentMap\' must not be null for type of \'" + type + "\'")
                    } else if (intentMap!!.isEmpty()) {
                        valid = false
                        mValidationErrors.add("\'intentMap\' must not be empty")
                    }
                    if (type == ConnectionType.HFP && serialDataType != DataType.ASCII) {
                        valid = false
                        mValidationErrors.add("\'serialDataType\' must be \'ascii\' for type of \'hfp\'")
                    }
                }
                ConnectionType.FILESTREAM -> {
                    if (operationsMap == null) {
                        valid = false
                        mValidationErrors.add("\'operations\' must not be null for type of \'" + type + "\'")
                    } else if (operationsMap!!.isEmpty()) {
                        valid = false
                        mValidationErrors.add("\'operations\' must not be empty")
                    }
                    if (files == null) {
                        valid = false
                        mValidationErrors.add("\'files\' must not be null for type of \'" + type + "\'")
                    } else if (files!!.size == 0) {
                        valid = false
                        mValidationErrors.add("\'files\' must not be empty")
                    } else {
                        for (file in files!!) {
                            if (!file.isValid) {
                                valid = false
                                mValidationErrors.addAll(file.validationErrors!!)
                            }
                        }
                    }
                }
            }
            return valid
        }

        val intentMap: IntentMap?
            get() = mIntentMap

        fun toStringBuilder(builder: StringBuilder, linePrefix: String): StringBuilder {
            var builder: StringBuilder? = builder
            if (builder == null) {
                builder = StringBuilder()
            }
            builder.append(linePrefix)
            builder.append("service (O for SPP, M for BLE): ")
            builder.append(if (service != null) service.toString() else "")
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("characteristic (O for SPP, M for BLE): ")
            builder.append(if (characteristic != null) characteristic.toString() else "")
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("pttDownKeyIntent (M): ")
            builder.append(pttDownKeyIntent)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("defaultPttDownKeyDelay (O): ")
            builder.append("" + defaultPttDownKeyDelay)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("intentDeDuplicate (O - default: true): ")
            builder.append("" + intentDeDuplicate)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("intentDeDuplicateTimeout (O - default: 50ms): ")
            builder.append("" + intentDeDuplicateTimeout)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("intentsDeDuplicateNoTimeout (O - default: []): ")
            builder.append("" + intentsDeDuplicateNoTimeout)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("eol (M if serialDataType is ascii): ")
            builder.append(eOL)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("serialDataType (M): ")
            builder.append(serialDataType)
            builder.append("\n")
            builder.append(linePrefix)
            builder.append("characteristicMaps ():\n")
            if (characteristicIntentMaps != null) {
                for ((key, value) in characteristicIntentMaps!!) {
                    builder.append(linePrefix)
                    builder.append("\t")
                    builder.append(key.toString())
                    builder.append(": ")
                    builder.append("\n")
                    for ((key1, value1) in value!!) {
                        builder.append(linePrefix)
                        builder.append("\t\t")
                        builder.append(key1)
                        builder.append(": ")
                        builder.append(value1)
                        builder.append("\n")
                    }
                }
            } else {
                builder.append("null\n")
                builder.append("\n")
            }
            builder.append(linePrefix)
            builder.append("intentMap ():\n")
            if (intentMap != null) {
                for ((key, value) in intentMap!!) {
                    builder.append(linePrefix)
                    builder.append("\t")
                    builder.append(key)
                    builder.append(": ")
                    builder.append(value)
                    builder.append("\n")
                }
            } else {
                builder.append("null\n")
                builder.append("\n")
            }
            builder.append(linePrefix)
            builder.append("operations (M for FILESTREAM): ")
            if (operationsMap != null) {
                for ((key, value) in operationsMap!!) {
                    builder.append(linePrefix)
                    builder.append("\t")
                    builder.append(key)
                    builder.append(": '")
                    builder.append(value)
                    builder.append("'\n")
                }
            } else {
                builder.append("null\n")
                builder.append("\n")
            }
            builder.append(linePrefix)
            builder.append("files (M for FILESTREAM): ")
            if (files != null) {
                for (file in files!!) {
                    builder.append(file.toStringBuilder(builder, linePrefix))
                }
            } else {
                builder.append("null\n")
                builder.append("\n")
            }
            return builder
        }

        fun toJsonString(): String {
            val stringOut = StringWriter()
            val writer = JsonWriter(stringOut)
            toJson(writer)
            return stringOut.toString()
        }

        fun toJson(writer: JsonWriter): JsonWriter {
            try {
                writer.beginObject()
                if (service != null) {
                    writer.name("service").value(service.toString())
                }
                if (characteristic != null) {
                    writer.name("characteristic").value(characteristic.toString())
                }
                writer.name("pttDownKeyIntent").value(pttDownKeyIntent)
                writer.name("defaultPttDownKeyDelay").value(defaultPttDownKeyDelay.toLong())
                writer.name("intentDeDuplicate").value(intentDeDuplicate)
                writer.name("intentDeDuplicateTimeout").value(intentDeDuplicateTimeout.toLong())
                writer.name("intentsDeDuplicateNoTimeout").beginArray()
                for (value in intentsDeDuplicateNoTimeout) {
                    writer.value(value)
                }
                writer.endArray()
                if (eOL != null) {
                    writer.name("eol").value(eOL)
                }
                writer.name("serialDataType").value(serialDataType.toString())
                if (characteristicIntentMaps != null) {
                    writer.name("characteristicMaps").beginObject()
                    for ((key, value) in characteristicIntentMaps!!) {
                        writer.name(key.toString()).beginObject()
                        for ((key1, value1) in value!!) {
                            writer.name(key1).value(value1)
                        }
                        writer.endObject()
                    }
                    writer.endObject()
                } else if (intentMap != null) {
                    writer.name("intentMap").beginObject()
                    for ((key, value) in intentMap!!) {
                        writer.name(key).value(value)
                    }
                    writer.endObject()
                }
                if (operationsMap != null) {
                    writer.name("operations").beginObject()
                    for ((key, value) in operationsMap!!) {
                        writer.name(key).value(value)
                    }
                    writer.endObject()
                }
                if (files != null) {
                    writer.name("files").beginArray()
                    for (file in files!!) {
                        file.toJson(writer)
                    }
                    writer.endArray()
                }
                writer.endObject()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return writer
        }

        override fun toString(): String {
            return toStringBuilder(StringBuilder(), "").toString()
        }
    }

    companion object {
        private val TAG = PttDriver::class.java.name
        @Throws(IOException::class)
        private fun readIntentMap(reader: JsonReader): IntentMap {
            val intentMap = IntentMap()
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                val value = reader.nextString()
                Log.v(TAG, "Intent mapping: $name -> $value")
                intentMap[name] = value
            }
            reader.endObject()
            return intentMap
        }

        fun escapeJsonString(value: String): String {
            try {
                val out: Writer = StringWriter()
                var i = 0
                val length = value.length
                while (i < length) {
                    val c = value[i]
                    when (c) {
                        '"', '\\' -> {
                            out.write('\\'.code)
                            out.write(c.code)
                        }
                        '\t' -> out.write("\\t")
                        '\b' -> out.write("\\b")
                        '\n' -> out.write("\\n")
                        '\r' -> out.write("\\r")
                        '\f' -> out.write("\\u000c")
                        '\u2028', '\u2029' -> out.write(String.format("\\u%04x", c.code))
                        else -> if (c.code <= 0x1F) {
                            out.write(String.format("\\u%04x", c.code))
                        } else {
                            out.write(c.code)
                        }
                    }
                    i++
                }
                val result = out.toString()
                Log.v(TAG, "escaped: $result")
                return result
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }
    }
}