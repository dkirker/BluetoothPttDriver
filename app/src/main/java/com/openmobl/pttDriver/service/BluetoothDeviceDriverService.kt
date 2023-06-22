package com.openmobl.pttDriver.service

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.os.*
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.openmobl.pttDriver.BuildConfig
import com.openmobl.pttDriver.Constants
import com.openmobl.pttDriver.R
import com.openmobl.pttDriver.bt.*
import com.openmobl.pttDriver.bt.hfp.AtCommandResult
import com.openmobl.pttDriver.model.*
import com.openmobl.pttDriver.model.PttDriver.ConnectionType
import com.openmobl.pttDriver.utils.SoundUtils
import com.openmobl.pttDriver.utils.TextUtil
import java.io.IOException
import java.util.*

class BluetoothDeviceDriverService : Service(), IDeviceDriverService, SerialListener {
    override var connectionState = DeviceConnectionState.Disconnected
        private set
    private var mEnabledSent = false
    private var mReconnectCount: Long = 0
    private var mLastReconnectAttempt: Date? = null
    private var mLastIntentSent: String? = null
    private var mLastIntentSentTime: Date? = null
    private var mSocket: SerialSocket? = null
    private var mPttDevice: BluetoothDevice? = null
    private var mPttWatchForDevice: BluetoothDevice? = null
    private var mPttDriver: PttDriver? = null
    private var mPttDeviceDelegate: BleDeviceDelegate? = null
    private var mConnectOnComplete = false
    override var automaticallyReconnect = false
    private var mPttDownKeyDelay = 0
    private var mPttDownKeyDelayOverride = false
    private var mCachedDeviceName: String? = ""
    private var mCachedDeviceAddress: String? = ""
    private var mNotificationBuilder: NotificationCompat.Builder? = null
    private var mBtDeviceConnectionState: MutableMap<BluetoothDevice?, String?>? = null
    private val mDeviceConnectReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            mBtDeviceConnectionState!![device] = action
            if (mPttDevice != null && device!!.address == mPttDevice!!.address) {
                if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                    Log.v(
                        TAG,
                        "Our device " + device!!.address + " has reconnected, reconnect to service"
                    )
                    if (connectionState != DeviceConnectionState.Connected) reconnectAutomatically()
                } /*else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    Log.v(TAG, "Our device " + device.getAddress() + " has disconnected, clean up");
                    disconnect();
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
                    Log.v(TAG, "Our device " + device.getAddress() + " has requested to disconnect");
                    //Device is about to disconnect
                    //disconnect();
                }*/
            }
        }
    }
    private var mReconnectTimerHandler: Handler? = null
    private val mReconnectCallback = Runnable {
        Log.v(TAG, "Try reconnect to device")
        connect()
    }
    private var mStatusListener: DeviceStatusListener? = null
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.v(TAG, "onStartCommand")
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "onCreate")
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        mReconnectTimerHandler = Handler(mainLooper)
        createNotification(getString(R.string.status_disconnected))
        mConnectOnComplete = false
        automaticallyReconnect = false
        mPttDownKeyDelay = 0
        mPttDownKeyDelayOverride = false
        mBtDeviceConnectionState = HashMap()
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(mDeviceConnectReceiver, filter)
    }

    override fun onDestroy() {
        Log.v(TAG, "onDestroy")
        if (connectionState != DeviceConnectionState.Disconnected) disconnect()
        try {
            unregisterReceiver(mDeviceConnectReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cancelNotification()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.v(TAG, "onBind")
        return DeviceDriverServiceBinder(this)
    }

    /*
        PTT Device parameters
     */
    override fun setPttDevice(device: Device?) {
        Log.v(TAG, "setPttDevice")
        if (device != null) {
            val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val btAdapter = btManager.adapter
            mPttDevice = btAdapter.getRemoteDevice(device.macAddress)
            mPttDownKeyDelay = device.pttDownDelay
            mPttDownKeyDelayOverride = true
            checkConnectOnComplete()
        } else {
            mPttDevice = null
        }
    }

    override fun deviceIsValid(): Boolean {
        return mPttDevice != null
    }

    // TODO: A separate device to watch for connecting
    fun setPttWatchForDevice(device: BluetoothDevice?) {
        mPttWatchForDevice = device
        //checkConnectOnComplete();
    }

    fun setPttWatchForDevice(name: String?) {
        try {
            //BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = manager.adapter
            //BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            val devices = bluetoothAdapter.bondedDevices
            for (device in devices) {
                if (device.name.contains(name!!)) {
                    setPttWatchForDevice(device)
                }
            }
        } catch (sec: SecurityException) {
            Log.d(TAG, "Received SecurityException -- BT permission not granted")
            sec.printStackTrace()
            // Display error.... or better transmit it
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun setPttDriver(driver: PttDriver?) {
        Log.v(TAG, "setPttDriver")
        mPttDriver = driver
        createDeviceDelegate()
        if (mPttWatchForDevice != null && mPttDriver.getWatchForDeviceName() != null) {
            setPttWatchForDevice(mPttDriver.getWatchForDeviceName())
        }
        checkConnectOnComplete()
    }

    private fun createDeviceDelegate() {
        if (mPttDriver != null && mPttDriver!!.isValid) {
            if (mPttDriver.getType() == ConnectionType.BLE && mPttDriver.getReadObj().characteristic == null) {
                mPttDeviceDelegate = BleDeviceDelegate()
                val characteristicMaps = mPttDriver.getReadObj().characteristicIntentMaps
                val readServiceUUID = mPttDriver.getReadObj().service
                mPttDeviceDelegate!!.addReadService(readServiceUUID)
                for ((key, characteristics) in characteristicMaps!!) {
                    mPttDeviceDelegate!!.addReadCharacteristic(readServiceUUID, key)
                }
                if (mPttDriver.getWriteObj() != null && mPttDriver.getWriteObj().service != null) {
                    mPttDeviceDelegate!!.addWriteCharacteristic(
                        mPttDriver.getWriteObj().service,
                        mPttDriver.getWriteObj().characteristic
                    )
                }
            } else if (mPttDriver.getType() == ConnectionType.BLE_SERIAL || mPttDriver.getType() == ConnectionType.BLE && mPttDriver.getReadObj().characteristic != null) {
                mPttDeviceDelegate = BleDeviceDelegate()
                mPttDeviceDelegate!!.addReadCharacteristic(
                    mPttDriver.getReadObj().service,
                    mPttDriver.getReadObj().characteristic
                )
                if (mPttDriver.getWriteObj() != null && mPttDriver.getWriteObj().service != null) {
                    mPttDeviceDelegate!!.addWriteCharacteristic(
                        mPttDriver.getWriteObj().service,
                        mPttDriver.getWriteObj().characteristic
                    )
                }
            }
        }
    }

    // Connect on complete signals the device driver to connect to the device when all necessary fields
    // have been set and are valid.
    var connectOnComplete: Boolean
        get() = mConnectOnComplete
        set(connectOnComplete) {
            mConnectOnComplete = connectOnComplete
            checkConnectOnComplete()
        }

    private fun checkConnectOnComplete() {
        if (mConnectOnComplete && mPttDevice != null && mPttDriver != null &&
            mPttDriver!!.isValid
        ) {
            // Don't connect if already connected...
            connect()
        }
    }

    var pttDownKeyDelay: Int
        get() = mPttDownKeyDelay
        set(delay) {
            mPttDownKeyDelay = delay
            mPttDownKeyDelayOverride = true
        }

    override fun registerStatusListener(statusListener: DeviceStatusListener?) {
        mStatusListener = statusListener
    }

    override fun unregisterStatusListener(statusListener: DeviceStatusListener?) {
        mStatusListener = null
    }

    /*
        Serial API
     */
    val deviceName: String?
        get() = if (mSocket != null) {
            mSocket.getName()
        } else null
    val deviceAddress: String?
        get() = if (mSocket != null) {
            mSocket.getAddress()
        } else null

    override fun connect() {
        Log.v(TAG, "connect()")
        if (connectionState == DeviceConnectionState.Disconnected) {
            status(R.string.status_connecting_to_device)
            try {
                //BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                //BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                status(R.string.status_connecting)
                connectionState = DeviceConnectionState.Pending
                mSocket = when (mPttDriver.getType()) {
                    ConnectionType.BLE, ConnectionType.BLE_SERIAL, ConnectionType.BLE_GAIA -> BleSerialSocket(
                        this,
                        mPttDevice,
                        mPttDeviceDelegate
                    )
                    ConnectionType.SPP, ConnectionType.SPP_GAIA -> SppSerialSocket(this, mPttDevice)
                    ConnectionType.HFP -> HfpSerialSocket(this, mPttDevice)
                    else -> return
                }
                mSocket!!.connect(this)
                createNotification(
                    if (mSocket != null) getString(R.string.connecting_to_prefix) + " " + deviceName else getString(
                        R.string.background_service
                    )
                )
            } catch (e: Exception) {
                Log.d(TAG, "Exception in connect(): $e")
                onSerialConnectError(e)
            }
        } else {
            Log.d(TAG, "State is not disconnected -- " + connectionState)
        }
    }

    override fun disconnect() {
        status(R.string.status_disconnecting)
        connectionState = DeviceConnectionState.Disconnected
        mEnabledSent = false
        createNotification(getString(R.string.status_disconnected))
        if (mSocket != null) {
            mSocket!!.disconnect()
            mSocket = null
        }
    }

    @Throws(IOException::class)
    private fun write(data: ByteArray) {
        if (connectionState == DeviceConnectionState.Disconnected) throw IOException("not connected")
        mSocket!!.write(data)
    }

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        onSerialConnect(null, null)
    }

    override fun onSerialConnect(service: UUID?, characteristic: UUID?) {
        status(R.string.status_connected)
        connectionState = DeviceConnectionState.Connected
        createNotification(
            if (mSocket != null) getString(R.string.connected_to_prefix) + " " + deviceName else getString(
                R.string.background_service
            )
        )
        mCachedDeviceName = deviceName
        mCachedDeviceAddress = deviceAddress
        DeviceEventBroadcaster.sendDeviceConnected(this, mCachedDeviceName, mCachedDeviceAddress)
        if (mStatusListener != null) {
            mStatusListener!!.onConnected()
        }
        if (!mEnabledSent) {
            val handler = Handler(mainLooper)
            handler.postDelayed({ enablePtt() }, 500)
        }
    }

    private fun playSoundIfEnabled(resId: Int) {
        // TODO: Create a pref for this
        try {
            SoundUtils.playSoundResource(applicationContext, resId, deviceAddress)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun enablePtt() {
        status(R.string.status_enabling)
        if (mPttDriver.getWriteObj() == null || mPttDriver.getWriteObj().startCmdStr == null ||
            mPttDriver.getWriteObj().startCmdStr.isEmpty()
        ) {
            status(R.string.status_connected)
            mEnabledSent = true
            playSoundIfEnabled(R.raw.sound_ptt_connected__hi)
            return
        }
        try {
            if (mPttDriver.getWriteObj().startCmdStr != null) {
                val startCmdStr = mPttDriver.getWriteObj().startCmdStr
                //String eolStr = mPttDriver.getWriteObj().getEOL();
                val startCmd =
                    if (mPttDriver.getWriteObj().startCmdStrType == PttDriver.DataType.ASCII) startCmdStr!!.toByteArray() else TextUtil.fromHexString(
                        startCmdStr
                    )
                Log.v(TAG, "Sending startCmd: " + startCmdStr + " (" + toHexString(startCmd) + ")")

                /*if (eolStr != null) {
                    byte[] eol = TextUtil.fromHexString(eolStr);
                    key = (new String(data)).replace(new String(eol), "");
                }*/write(startCmd)
            } /*else {
                byte[] data = { 0x00 };

                write(data);
            }*/
        } catch (e: Exception) {
            //if (!e.getMessage().equalsIgnoreCase("cannot write to device")) {
            onSerialIoError(e)
            //}
        }
        try {
            val handler = Handler(mainLooper)
            handler.postDelayed({
                status(R.string.status_connected)
                // TODO: We really need to confirm we are connected
                playSoundIfEnabled(R.raw.sound_ptt_connected__hi)
            }, 500)
            mEnabledSent = true
        } catch (e: Exception) {
            //if (!e.getMessage().equalsIgnoreCase("cannot write to device")) {
            onSerialIoError(e)
            //}
        }
    }

    override fun onSerialConnectError(e: Exception) {
        status(getString(R.string.status_prefix_connection_failed) + " " + e.message)
        connectionState = DeviceConnectionState.Disconnected
        if (mStatusListener != null) {
            mStatusListener!!.onDisconnected()
        }
        DeviceEventBroadcaster.sendDeviceDisconnected(this, mCachedDeviceName, mCachedDeviceAddress)
        disconnect()
        reconnectAutomatically()
    }

    override fun onSerialDisconnect() {
        status(R.string.status_disconnected)
        mEnabledSent = false
        connectionState = DeviceConnectionState.Disconnected
        createNotification(getString(R.string.status_disconnected))
        DeviceEventBroadcaster.sendDeviceDisconnected(this, mCachedDeviceName, mCachedDeviceAddress)
        if (mStatusListener != null) {
            mStatusListener!!.onDisconnected()
        }
        reconnectAutomatically()
    }

    private fun deviceIsConnected(device: BluetoothDevice?): Boolean {
        val state = if (device != null) mBtDeviceConnectionState!![device] else null
        Log.v(TAG, "deviceIsConnected bluetooth device state: $state")
        val connected = BluetoothDevice.ACTION_ACL_CONNECTED == state
        Log.v(TAG, "deviceIsConnected is $connected")
        return connected || device != null && internal_isConnected(device)
    }

    private fun deviceIsConnected(macAddress: String): Boolean {
        var device: BluetoothDevice? = null
        for (dev in mBtDeviceConnectionState!!.keys) {
            if (macAddress == dev!!.address) {
                device = dev
            }
        }
        return deviceIsConnected(device)
    }

    private fun reconnectAutomatically() {
        // If we are waiting on a reconnect attempt then bail out so that we aren't resetting our attempt,
        // or flooding the system.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mReconnectTimerHandler!!.hasCallbacks(
                mReconnectCallback
            )
        ) {
            Log.d(TAG, "Reconnect attempt pending, don't schedule more")
            return
        }
        if (automaticallyReconnect) {
            Log.d(TAG, "Check reconnect automatically")
            try {
                var shouldReconnect = deviceIsConnected(mPttDevice)
                Log.v(TAG, "shouldReconnect $shouldReconnect")
                if (!shouldReconnect && mPttWatchForDevice != null) {
                    shouldReconnect = deviceIsConnected(mPttWatchForDevice)
                }
                Log.v(TAG, "shouldReconnect $shouldReconnect")
                if (shouldReconnect) {
                    val now = Date()
                    if (mLastReconnectAttempt != null &&
                        now.time - mLastReconnectAttempt!!.time > RECONNECT_COUNT_RESET_MILLI
                    ) {
                        mReconnectCount = 0
                    } else if (mReconnectCount > RECONNECT_COUNT_RESET_AFTER) {
                        mReconnectCount = 0
                    }
                    status(R.string.status_reconnecting)
                    mReconnectCount++
                    mLastReconnectAttempt = now
                    Log.v(TAG, "Attempting reconnect in " + 1000 * mReconnectCount + "ms")
                    mReconnectTimerHandler!!.postDelayed(mReconnectCallback, 1000 * mReconnectCount)
                }
            } catch (e: Exception) {
                status(getString(R.string.status_prefix_failed_to_reconnect) + " " + e.message)
                e.printStackTrace()
            }
        }
    }

    private fun sendIntentInternal(intentName: String) {
        // Possibility that user pressed the key down and released it before the key down delay.
        // There might be an edge case here, so we *might* want to also include a timestamp with the
        // call to sendIntentInternal.
        if (intentName != mLastIntentSent) {
            return
        }
        try {
            val intent = Intent()
            Log.d(TAG, "Sending intent: $intentName")
            if (intentName.contains(":") && intentName.contains(",")) {
                val eventData = intentName.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[1]
                val newName = intentName.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[0]
                val keyCode = KeyEvent.keyCodeFromString(
                    eventData.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[0])
                val keyAction = eventData.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[1].toInt()
                Log.v(
                    TAG, "Sending KeyEvent Intent " + newName + " keyCode: " +
                            keyCode + " keyAction: " + keyAction
                )
                val event = KeyEvent(
                    SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    keyAction, keyCode, 0
                )
                intent.action = newName
                intent.putExtra(Intent.EXTRA_KEY_EVENT, event)
                sendBroadcast(intent)
            } else {
                intent.action = intentName
                sendBroadcast(intent)
            }
            // libsu and `am broadcast -a <intentName>` to support protected intents?
        } catch (e: Exception) {
            Log.d(TAG, "Exception sending intent: $e")
        }
    }

    private fun sendIntent(intentName: String, delay: Int = 0) {
        val now = Date()
        if (mLastIntentSent != null && mPttDriver.getReadObj().intentsDeDuplicateNoTimeout.size > 0 && mLastIntentSent == intentName && mPttDriver.getReadObj().intentsDeDuplicateNoTimeout.contains(
                intentName
            )
        ) {
            return
        }
        if (mPttDriver.getReadObj().intentDeDuplicate && mLastIntentSentTime != null && mLastIntentSent != null && mLastIntentSent == intentName && now.time - mLastIntentSentTime!!.time < mPttDriver.getReadObj().intentDeDuplicateTimeout) {
            return
        }
        mLastIntentSent = intentName
        // We might consider accounting for the delay...
        mLastIntentSentTime = Date()
        if (delay > 0) {
            val handler = Handler(mainLooper)
            handler.postDelayed({ sendIntentInternal(intentName) }, delay.toLong())
        } else {
            sendIntentInternal(intentName)
        }
    }

    override fun onSerialRead(data: ByteArray?, service: UUID?, characteristic: UUID?) {
        var intentName: String? = null
        var key = if (mPttDriver.getReadObj().serialDataType == PttDriver.DataType.ASCII) String(
            data!!
        ) else toHexString(data)
        Log.v(
            TAG,
            "onSerialRead - data = " + toHexString(data) + ", serialDataType = " + mPttDriver.getReadObj().serialDataType +
                    ", key = " + key
        )
        if (mPttDriver.getType() == ConnectionType.BLE_SERIAL || mPttDriver.getType() == ConnectionType.SPP || mPttDriver.getType() == ConnectionType.HFP || mPttDriver.getType() == ConnectionType.BLE && mPttDriver.getReadObj().characteristic != null) {
            val eolStr = mPttDriver.getReadObj().eol
            Log.v(TAG, "Read from intent map")
            if (eolStr != null) {
                val eol = TextUtil.fromHexString(eolStr)
                key = String(data).replace(String(eol!!), "")
            }
            Log.d(TAG, "Received button press: $key")
            val pttIntentMap =
                if (mPttDriver.getReadObj() != null) mPttDriver.getReadObj().intentMap else null

            /*if (pttIntentMap != null) {
                Log.v(TAG, "Intent map: " + pttIntentMap.toString());
                Log.v(TAG, "Hex key: " + TextUtil.toHexString(key.getBytes()));
            } else {
                Log.v(TAG, "No intent map! :(");
            }*/if (pttIntentMap != null && pttIntentMap.containsKey(key)) {
                intentName = pttIntentMap[key]
            }
            Log.v(TAG, "Mapped intent: $intentName")
            if (mPttDriver.getType() == ConnectionType.HFP) {
                // If we aren't doing anything with this data then pass it to the HFP engine
                if (intentName == null) {
                    (mSocket as HfpSerialSocket?)!!.processAtCommands(String(data!!))
                } else { // Otherwise, acknowledge it
                    val result = AtCommandResult(AtCommandResult.Companion.OK)
                    try {
                        write(result.toString().toByteArray())
                    } catch (e: Exception) {
                        Log.d(TAG, "Exception in sending AtCommandResult.OK")
                        e.printStackTrace()
                    }
                }
            }
        } else if (mPttDriver.getType() == ConnectionType.BLE) {
            Log.v(TAG, "Read from characteristic intent maps")
            if (key.length > 0) {
                key = key.replace("\\s".toRegex(), "")
                Log.d(TAG, "Received button press: $key")
                val pttCharacteristicsIntentMap =
                    if (mPttDriver.getReadObj() != null) mPttDriver.getReadObj().characteristicIntentMaps else null
                if (pttCharacteristicsIntentMap != null && pttCharacteristicsIntentMap.containsKey(
                        characteristic
                    ) && pttCharacteristicsIntentMap[characteristic] != null &&
                    pttCharacteristicsIntentMap[characteristic]!!.containsKey(key)
                ) {
                    intentName = pttCharacteristicsIntentMap[characteristic]!![key]
                }
            }
        } else if (mPttDriver.getType() == ConnectionType.BLE_GAIA ||
            mPttDriver.getType() == ConnectionType.SPP_GAIA
        ) {
        }
        if (intentName != null) {
            var delay = 0
            if (mPttDriver.getReadObj().pttDownKeyIntent != null && intentName == mPttDriver.getReadObj().pttDownKeyIntent) {
                // If the user did not override the ptt key down delay, check to see what the driver defines
                delay =
                    if (mPttDownKeyDelayOverride) mPttDownKeyDelay else mPttDriver.getReadObj().defaultPttDownKeyDelay
            }
            sendIntent(intentName, delay)
        }
    }

    override fun onSerialIoError(e: Exception) {
        status(getString(R.string.status_prefix_connection_lost) + " " + e.message)
        disconnect()
        connectionState = DeviceConnectionState.Disconnected

        // Are we actually "disconnected"?
        createNotification(getString(R.string.status_disconnected))
        DeviceEventBroadcaster.sendDeviceDisconnected(this, mCachedDeviceName, mCachedDeviceAddress)
        if (mStatusListener != null) {
            mStatusListener!!.onDisconnected()
        }
        reconnectAutomatically()
    }

    override fun onBatteryEvent(level: Byte) {
        if (connectionState != DeviceConnectionState.Disconnected) {
            createNotification(
                (if (mSocket != null) getString(R.string.connected_to_prefix) + " " + deviceName else getString(
                    R.string.background_service
                )) +
                        " - " + getString(R.string.battery_prefix) + " " + level + "%"
            )
            DeviceEventBroadcaster.sendDeviceBatteryState(
                this,
                mCachedDeviceName,
                mCachedDeviceAddress,
                level.toFloat()
            )
            if (mStatusListener != null) {
                mStatusListener!!.onBatteryEvent(level)
            }
        }
    }

    private fun createNotification(message: String) {
        var channelId = ""
        val manager = NotificationManagerCompat.from(this)
        var newNotif = false
        if (mNotificationBuilder == null) {
            newNotif = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channelId =
                    BuildConfig.APPLICATION_ID + "." + BluetoothDeviceDriverService::class.java.name
                val channelName = getString(R.string.bluetooth_background_service)
                val chan = NotificationChannelCompat.Builder(
                    channelId,
                    NotificationManagerCompat.IMPORTANCE_LOW
                )
                    .setName(channelName)
                    .build()
                manager.createNotificationChannel(chan)
            }
            mNotificationBuilder = NotificationCompat.Builder(this, channelId)
        }
        val disconnectIntent = Intent(Constants.INTENT_ACTION_DISCONNECT)
        val reconnectIntent = Intent(Constants.INTENT_ACTION_RECONNECT)
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            this, 1,
            disconnectIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val reconnectPendingIntent = PendingIntent.getBroadcast(
            this, 2,
            reconnectIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mNotificationBuilder!!.clearActions()
        if (connectionState != DeviceConnectionState.Disconnected) {
            mNotificationBuilder!!.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_clear_white_24dp,
                    getString(R.string.disconnect), disconnectPendingIntent
                )
            )
        } else {
            mNotificationBuilder!!.addAction(
                NotificationCompat.Action(
                    R.drawable.ic_clear_white_24dp,
                    getString(R.string.reconnect), reconnectPendingIntent
                )
            )
        }
        val appIntent = Intent()
            .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val appPendingIntent = PendingIntent.getActivity(
            this, 0,
            appIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        /*if (mNotificationBuilder == null) {
            mNotificationBuilder = new NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(getResources().getColor(R.color.colorPrimary))
                    .setContentTitle(getResources().getString(R.string.app_name))
                    .setContentText(mSocket != null ? "Connected to " + mSocket.getName() : "Background Service")
                    .setContentIntent(restartPendingIntent)
                    .setOngoing(true)
                    .addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp, "Disconnect", disconnectPendingIntent));
            // @drawable/ic_notification created with Android Studio -> New -> Image Asset using @color/colorPrimaryDark as background color
            // Android < API 21 does not support vectorDrawables in notifications, so both drawables used here, are created as .png instead of .xml
        }*/

        // app name is always displayed in notification on >= O
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            mNotificationBuilder!!.setContentTitle(getString(R.string.app_name))
        }
        mNotificationBuilder!!.setContentText(message)
        mNotificationBuilder!!.setSmallIcon(R.drawable.ic_notification)
        mNotificationBuilder!!.color = resources.getColor(R.color.colorPrimary)
        mNotificationBuilder!!.priority = NotificationCompat.PRIORITY_DEFAULT
        mNotificationBuilder!!.setCategory(NotificationCompat.CATEGORY_CALL)
        mNotificationBuilder!!.setShowWhen(false)
        mNotificationBuilder!!.setOngoing(true)
        mNotificationBuilder!!.setContentIntent(appPendingIntent)
        val notification = mNotificationBuilder!!.build()
        if (newNotif) {
            startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification)
        } else {
            manager.notify(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification)
        }
    }

    private fun cancelNotification() {
        stopForeground(true)
    }

    private fun status(resId: Int) {
        status(getString(resId))
    }

    private fun status(status: String) {
        Log.d(TAG, status)
        if (mStatusListener != null) {
            mStatusListener!!.onStatusMessageUpdate(status)
        }
    }

    companion object {
        private val TAG = BluetoothDeviceDriverService::class.java.name

        // If we stay connected for more than two minutes, we can reset the reset count
        private const val RECONNECT_COUNT_RESET_MILLI: Long = 120000

        // If we try to reconnect more than this many times reset the count which resets the back-off delay
        private const val RECONNECT_COUNT_RESET_AFTER: Long = 60
        private fun internal_isConnected(device: BluetoothDevice): Boolean {
            return try {
                val m = device.javaClass.getMethod("isConnected", *null as Array<Class<*>?>?)
                val connected = m.invoke(device, *null as Array<Any?>?) as Boolean
                Log.v(TAG, "internal_isConnected $connected")
                connected
            } catch (e: Exception) {
                Log.v(TAG, "internal_isConnected threw exception $e")
                false
            }
        }
    }
}