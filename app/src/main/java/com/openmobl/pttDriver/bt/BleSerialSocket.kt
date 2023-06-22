package com.openmobl.pttDriver.bt

import android.app.Activity
import android.bluetooth.*
import android.content.*
import android.os.Build
import android.util.Log
import com.openmobl.pttDriver.Constants
import com.openmobl.pttDriver.R
import com.openmobl.pttDriver.bt.BleSerialSocket
import java.io.IOException
import java.security.InvalidParameterException
import java.util.*

/**
 * wrap BLE communication into socket like class
 * - connect, disconnect and write as methods,
 * - read + status is returned by SerialListener
 */
class BleSerialSocket(context: Context, device: BluetoothDevice?) : BluetoothGattCallback(),
    SerialSocket {
    private class WriteBufferItem(
        val data: ByteArray?,
        val btCharacteristic: BluetoothGattCharacteristic?
    )

    private val mWriteBuffer: ArrayList<WriteBufferItem>
    private val mCcdQueue: ArrayList<WriteBufferItem>
    private val mPairingIntentFilter: IntentFilter
    private val mPairingBroadcastReceiver: BroadcastReceiver
    private val mDisconnectBroadcastReceiver: BroadcastReceiver
    private val mReconnectBroadcastReceiver: BroadcastReceiver
    private val mContext: Context
    private var mListener: SerialListener? = null
    private var mDelegate: BleDeviceDelegate? = null
    private var mDevice: BluetoothDevice?
    private var mGatt: BluetoothGatt? = null
    private var mWritePending = false
    private var mCanceled = false
    private var mConnected = false
    private var payloadSize = DEFAULT_MTU - 3

    init {
        Log.d(TAG, "BleSerialSocket")
        if (context is Activity) throw InvalidParameterException("expected non UI context")
        mContext = context
        mDevice = device
        mWriteBuffer = ArrayList()
        mCcdQueue = ArrayList()
        mPairingIntentFilter = IntentFilter()
        mPairingIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        mPairingIntentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        mPairingBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                onPairingBroadcastReceive(context, intent)
            }
        }
        mDisconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                //if(mListener != null)
                //    mListener.onSerialIoError(new IOException("background disconnect"));
                disconnect(true) // disconnect now, else would be queued until UI re-attached
            }
        }
        mReconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                //if(mListener != null)
                //    mListener.onSerialIoError(new IOException("background reconnect"));
                val oldListener = mListener
                disconnect(true) // disconnect now, else would be queued until UI re-attached
                try {
                    connect(oldListener)
                } catch (e: IOException) {
                    Log.d(TAG, "reconnect failed")
                    e.printStackTrace()
                }
            }
        }
    }

    constructor(
        context: Context,
        device: BluetoothDevice?,
        deviceDelegate: BleDeviceDelegate?
    ) : this(context, device) {
        mDelegate = deviceDelegate
    }

    fun setDeviceDelegate(deviceDelegate: BleDeviceDelegate?) {
        mDelegate = deviceDelegate
    }

    override val name: String?
        get() = if (mDevice!!.name != null) mDevice!!.name else mDevice!!.address
    override val address: String?
        get() = mDevice!!.address

    private fun registerReceivers() {
        mContext.registerReceiver(
            mDisconnectBroadcastReceiver,
            IntentFilter(Constants.INTENT_ACTION_DISCONNECT)
        )
        mContext.registerReceiver(
            mReconnectBroadcastReceiver,
            IntentFilter(Constants.INTENT_ACTION_RECONNECT)
        )
        mContext.registerReceiver(mPairingBroadcastReceiver, mPairingIntentFilter)
    }

    private fun unregisterReceivers() {
        try {
            mContext.unregisterReceiver(mPairingBroadcastReceiver)
        } catch (ignored: Exception) {
        }
        try {
            mContext.unregisterReceiver(mDisconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
        try {
            mContext.unregisterReceiver(mReconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
    }

    override fun disconnect() {
        disconnect(false)
    }

    override fun disconnect(silent: Boolean) {
        Log.d(TAG, "disconnect")
        //mListener = null; // ignore remaining data and errors
        mDevice = null
        mCanceled = true
        synchronized(mWriteBuffer) {
            mWritePending = false
            mWriteBuffer.clear()
        }
        if (mDelegate != null) mDelegate!!.disconnect()
        if (mGatt != null) {
            try {
                Log.d(TAG, "gatt.disconnect")
                mGatt!!.disconnect()
                Log.d(TAG, "gatt.close")
                mGatt!!.close()
            } catch (e: Exception) {
                Log.d(TAG, "Exception while disconnecting and cleaning up: $e")
                e.printStackTrace()
            }
            mGatt = null
        }
        mConnected = false
        unregisterReceivers()
        if (!silent && mListener != null) onSerialDisconnect()
        //mListener = null; // ignore remaining data and errors
    }

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    @Throws(IOException::class)
    override fun connect(listener: SerialListener?) {
        if (mConnected || mGatt != null) throw IOException("already connected")
        mCanceled = false
        mListener = listener
        unregisterReceivers()
        registerReceivers()
        Log.d(TAG, "connect $mDevice")
        mGatt = if (Build.VERSION.SDK_INT < 23) {
            Log.d(TAG, "connectGatt")
            mDevice!!.connectGatt(mContext, false, this)
        } else {
            Log.d(TAG, "connectGatt,LE")
            mDevice!!.connectGatt(mContext, false, this, BluetoothDevice.TRANSPORT_LE)
        }
        if (mGatt == null) throw IOException("connectGatt failed")
        // continues asynchronously in onPairingBroadcastReceive() and onConnectionStateChange()
    }

    private fun onPairingBroadcastReceive(context: Context, intent: Intent) {
        // for ARM Mbed, Microbit, ... use pairing from Android bluetooth settings
        // for HM10-clone, ... pairing is initiated here
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        if (device == null || device != mDevice) return
        when (intent.action) {
            BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                val pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                Log.d(TAG, "pairing request $pairingVariant")
                onSerialConnectError(IOException(context.getString(R.string.pairing_request)))
            }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                val previousBondState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                Log.d(TAG, "bond state $previousBondState->$bondState")
            }
            else -> Log.d(TAG, "unknown broadcast " + intent.action)
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        Log.v(TAG, "Received onConnectionStateChange: $status, $newState")

        // status directly taken from gat_api.h, e.g. 133=0x85=GATT_ERROR ~= timeout
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG, "connect status $status, discoverServices")
            if (!gatt.discoverServices()) onSerialConnectError(IOException("discoverServices failed"))
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (mConnected) onSerialIoError(IOException("gatt status $status")) else onSerialConnectError(
                IOException(
                    "gatt status $status"
                )
            )
        } else {
            Log.d(TAG, "unknown connect state $newState $status")
        }
        // continues asynchronously in onServicesDiscovered()
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        Log.d(TAG, "servicesDiscovered, status $status")
        if (mCanceled) return
        connectCharacteristics1(gatt)
    }

    private fun connectCharacteristics1(gatt: BluetoothGatt) {
        var sync = true
        mWritePending = false
        if (mDelegate != null) {
            var syncCount = 0
            for (gattService in gatt.services) {
                Log.d(TAG, "gattService = " + gattService.uuid)
                val serviceUUID = gattService.uuid
                if (mDelegate!!.containsReadService(serviceUUID)) {
                    val characteristics = mDelegate!!.getReadCharacteristics(serviceUUID)
                    Log.v(TAG, "Setting GATT interface for read service $serviceUUID")
                    for (characteristic in characteristics!!) {
                        syncCount++
                        Log.v(TAG, "Setting GATT interface for read characteristic $characteristic")
                        mDelegate!!.setReadCharacteristic(
                            serviceUUID,
                            characteristic,
                            gattService.getCharacteristic(characteristic)
                        )
                    }
                }
                if (mDelegate!!.containsWriteService(serviceUUID)) {
                    val characteristics = mDelegate!!.getWriteCharacteristics(serviceUUID)
                    Log.v(TAG, "Setting GATT interface for write service $serviceUUID")
                    for (characteristic in characteristics!!) {
                        syncCount++
                        Log.v(
                            TAG,
                            "Setting GATT interface for write characteristic $characteristic"
                        )
                        mDelegate!!.setWriteCharacteristic(
                            serviceUUID,
                            characteristic,
                            gattService.getCharacteristic(characteristic)
                        )
                    }
                }
                if (serviceUUID == BLUETOOTH_LE_BATTERY_SERVICE) {
                    syncCount++
                    Log.v(
                        TAG,
                        "Setting GATT interface for battery service " + BLUETOOTH_LE_BATTERY_SERVICE
                    )
                    mDelegate!!.addReadService(BLUETOOTH_LE_BATTERY_SERVICE)
                    mDelegate!!.addReadCharacteristic(
                        BLUETOOTH_LE_BATTERY_SERVICE,
                        BLUETOOTH_LE_BATTERY_CHARACTERISTIC
                    )
                    Log.v(
                        TAG,
                        "Setting GATT interface for battery characteristic " + BLUETOOTH_LE_BATTERY_CHARACTERISTIC
                    )
                    mDelegate!!.setReadCharacteristic(
                        serviceUUID,
                        BLUETOOTH_LE_BATTERY_CHARACTERISTIC,
                        gattService.getCharacteristic(
                            BLUETOOTH_LE_BATTERY_CHARACTERISTIC
                        )
                    )
                }
            }
            sync = syncCount > 0
        }
        if (mCanceled) return
        if (mDelegate == null || !sync) {
            Log.d(
                TAG,
                "No serial profile found (mDelegate == null: " + (mDelegate == null) + ", !sync = " + !sync + ")"
            )
            for (gattService in gatt.services) {
                Log.d(TAG, "service " + gattService.uuid)
                for (characteristic in gattService.characteristics) Log.d(
                    TAG,
                    "characteristic " + characteristic.uuid
                )
            }
            onSerialConnectError(IOException("no serial profile found"))
            return
        }
        connectCharacteristics2(gatt)
    }

    private fun connectCharacteristics2(gatt: BluetoothGatt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "request max MTU")
            if (!gatt.requestMtu(MAX_MTU)) onSerialConnectError(IOException("request MTU failed"))
            // continues asynchronously in onMtuChanged
        } else {
            connectCharacteristics3(gatt)
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        Log.d(TAG, "mtu size $mtu, status=$status")
        if (status == BluetoothGatt.GATT_SUCCESS) {
            payloadSize = mtu - 3
            Log.d(TAG, "payload size $payloadSize")
        }
        connectCharacteristics3(gatt)
    }

    private fun connectCharacteristics3(gatt: BluetoothGatt) {
        for (characteristic in mDelegate.getAllWriteCharacteristics()) {
            try {
                val writeProperties = characteristic!!.properties
                if (writeProperties and BluetoothGattCharacteristic.PROPERTY_WRITE +  // Microbit,HM10-clone have WRITE
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == 0
                ) { // HM10,TI uart,Telit have only WRITE_NO_RESPONSE
                    onSerialConnectError(IOException("write characteristic " + characteristic!!.uuid + " not writable"))
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(TAG, "Exception setting up write characteristic")
            }
        }
        for (characteristic in mDelegate.getAllReadCharacteristics()) {
            try {
                if (!gatt.setCharacteristicNotification(characteristic, true)) {
                    onSerialConnectError(IOException("no notification for read characteristic " + characteristic!!.uuid))
                    return
                }

                /*BluetoothGattDescriptor readDescriptor = characteristic.getDescriptor(BLUETOOTH_LE_CCCD);

                if (readDescriptor == null) {
                    onSerialConnectError(new IOException("no CCCD descriptor for read characteristic " + characteristic.getUuid()));
                    return;
                }

                int readProperties = characteristic.getProperties();
                byte[] valueToSend;

                if ((readProperties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    Log.d(TAG, "enable read indication for " + characteristic.getUuid());
                    valueToSend = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                } else if ((readProperties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    Log.d(TAG, "enable read notification for " + characteristic.getUuid());
                    valueToSend = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    onSerialConnectError(new IOException("no indication/notification for read characteristic (" + readProperties + ")"));
                    return;
                }

                Log.d(TAG, "writing read characteristic descriptor");

                if (!gatt.writeDescriptor(readDescriptor)) {
                    Log.d(TAG, "read characteristic CCCD descriptor not writable " + characteristic.getUuid());
                    //onSerialConnectError(new IOException("read characteristic CCCD descriptor not writable " + characteristic.getUuid()));
                }*/
                val readProperties = characteristic!!.properties
                var valueToSend: ByteArray?
                valueToSend =
                    if (readProperties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                        Log.d(TAG, "enable read indication for " + characteristic!!.uuid)
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    } else if (readProperties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                        Log.d(TAG, "enable read notification for " + characteristic!!.uuid)
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        onSerialConnectError(IOException("no indication/notification for read characteristic ($readProperties)"))
                        return
                    }
                Log.d(
                    TAG,
                    "Add read characteristic descriptor to queue for " + characteristic!!.uuid
                )
                mCcdQueue.add(WriteBufferItem(valueToSend, characteristic))

                // continues asynchronously in onDescriptorWrite()
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(TAG, "Exception setting up read characteristic")
            }
        }
        processCcdQueueItem(gatt)
    }

    // TODO: There are edge cases around this code right now...
    private fun processCcdQueueItem(gatt: BluetoothGatt) {
        if (mCcdQueue.size > 0) {
            try {
                val queueItem = mCcdQueue.removeAt(0)
                val characteristic: BluetoothGattCharacteristic = queueItem.getBtCharacteristic()
                val readDescriptor = characteristic.getDescriptor(BLUETOOTH_LE_CCCD)
                if (readDescriptor == null) {
                    Log.d(TAG, "no CCCD descriptor for read characteristic " + characteristic.uuid)
                    //onSerialConnectError(new IOException("no CCCD descriptor for read characteristic " + characteristic.getUuid()));
                    processCcdQueueItem(gatt)
                    return
                }
                readDescriptor.value = queueItem.getData()
                if (!gatt.writeDescriptor(readDescriptor)) {
                    Log.d(
                        TAG,
                        "read characteristic CCCD descriptor not writable " + characteristic.uuid
                    )
                    //onSerialConnectError(new IOException("read characteristic CCCD descriptor not writable " + characteristic.getUuid()));
                    processCcdQueueItem(gatt)
                    return
                }
            } catch (e: Exception) {
                Log.d(TAG, "Received exception in processCcdQueueItem: $e")
                e.printStackTrace()
                processCcdQueueItem(gatt)
            }
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int
    ) {
        mDelegate!!.onDescriptorWrite(gatt, descriptor, status)
        if (mCanceled) return
        if (mDelegate!!.containsReadCharacteristic(
                descriptor.characteristic.service.uuid,
                descriptor.characteristic.uuid
            )
        ) {
            Log.d(
                TAG,
                "writing read characteristic descriptor for " + descriptor.characteristic.uuid + "finished, status=" + status
            )
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onSerialConnectError(IOException("write descriptor failed"))
            } else {
                // onCharacteristicChanged with incoming data can happen after writeDescriptor(ENABLE_INDICATION/NOTIFICATION)
                // before confirmed by this method, so receive data can be shown before device is shown as 'Connected'.
                /*onSerialConnect(descriptor.getCharacteristic().getService().getUuid(),
                        descriptor.getCharacteristic().getUuid());
                mConnected = true;
                Log.d(TAG, "connected");*/
                if (mCcdQueue.size > 0) {
                    processCcdQueueItem(gatt)
                } else {
                    onSerialConnect()
                    mConnected = true
                    Log.d(TAG, "connected")
                }
            }
        }
    }

    /*
     * read
     */
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (mCanceled) return
        mDelegate!!.onCharacteristicChanged(gatt, characteristic)
        if (mCanceled) return
        if (BLUETOOTH_LE_BATTERY_CHARACTERISTIC == characteristic.uuid && BLUETOOTH_LE_BATTERY_SERVICE == characteristic.service.uuid) {
            val data = characteristic.value
            onBatteryEvent(data)
        } else if (mDelegate!!.containsReadCharacteristic(
                characteristic.service.uuid,
                characteristic.uuid
            )
        ) { // NOPMD - test object identity
            val data = characteristic.value
            Log.d(
                TAG,
                "read (onCharacteristicChanged), len = " + data.size + ", data = " + toHexString(
                    data
                )
            )
            onSerialRead(data, characteristic.service.uuid, characteristic.uuid)
        }
    }

    /*
     * write
     */
    @Throws(IOException::class)
    override fun write(data: ByteArray) {
        if (mCanceled || !mConnected || mDelegate == null) throw IOException("not connected")
        val services = mDelegate.getWriteServices()
        if (services!!.size > 0) {
            val service = services!![0]
            val characteristics = mDelegate!!.getWriteCharacteristics(service)
            if (characteristics!!.size > 0) {
                write(data, service, characteristics[0])
            } else {
                throw IOException("Could not write to device - no write characteristics")
            }
        } else {
            throw IOException("Could not write to device - no write services")
        }
    }

    @Throws(IOException::class)
    fun write(data: ByteArray, service: UUID?, characteristic: UUID?) {
        if (mCanceled || !mConnected || mDelegate == null) throw IOException("not connected")
        if (service == null || characteristic == null) throw IOException("cannot write to device")
        write(data, mDelegate!!.getWriteCharacteristic(service, characteristic))
    }

    @Throws(IOException::class)
    fun write(data: ByteArray, characteristic: BluetoothGattCharacteristic?) {
        if (mCanceled || !mConnected || mDelegate == null) throw IOException("not connected")
        if (characteristic == null) throw IOException("cannot write to device")
        var data0: ByteArray?
        synchronized(mWriteBuffer) {
            data0 = if (data.size <= payloadSize) {
                data
            } else {
                Arrays.copyOfRange(data, 0, payloadSize)
            }
            if (!mWritePending && mWriteBuffer.isEmpty() && mDelegate!!.canWrite()) {
                mWritePending = true
            } else {
                mWriteBuffer.add(WriteBufferItem(data0, characteristic))
                Log.d(TAG, "write queued, len=" + data0!!.size)
                data0 = null
            }
            if (data.size > payloadSize) {
                for (i in (1 until data.size + payloadSize - 1) / payloadSize) {
                    val from = i * payloadSize
                    val to = Math.min(from + payloadSize, data.size)
                    mWriteBuffer.add(
                        WriteBufferItem(
                            Arrays.copyOfRange(data, from, to),
                            characteristic
                        )
                    )
                    Log.d(TAG, "write queued, len=" + (to - from))
                }
            }
        }
        if (data0 != null) {
            characteristic.value = data0
            if (!mGatt!!.writeCharacteristic(characteristic)) {
                onSerialIoError(IOException("write failed"))
            } else {
                Log.d(TAG, "write started, len=" + data0!!.size)
            }
        }
        // continues asynchronously in onCharacteristicWrite()
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        status: Int
    ) {
        if (mCanceled || !mConnected) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            onSerialIoError(IOException("write failed"))
            return
        }
        mDelegate!!.onCharacteristicWrite(gatt, characteristic, status)
        if (mCanceled) return
        if (mDelegate!!.containsWriteCharacteristic(
                characteristic.service.uuid,
                characteristic.uuid
            )
        ) { // NOPMD - test object identity
            Log.d(TAG, "write finished, status=$status")
            writeNext()
        }
    }

    private fun writeNext() {
        val data: ByteArray?
        var characteristic: BluetoothGattCharacteristic?
        synchronized(mWriteBuffer) {
            if (!mWriteBuffer.isEmpty() && mDelegate!!.canWrite()) {
                mWritePending = true
                val packet = mWriteBuffer.removeAt(0)
                data = packet.getData()
                characteristic = packet.getBtCharacteristic()
            } else {
                mWritePending = false
                data = null
                characteristic = null
            }
        }
        if (data != null && characteristic != null) {
            characteristic!!.value = data
            if (!mGatt!!.writeCharacteristic(characteristic)) {
                onSerialIoError(IOException("write failed"))
            } else {
                Log.d(TAG, "write started, len=" + data.size)
            }
        }
    }

    /**
     * SerialListener
     */
    private fun onSerialConnect() {
        if (mListener != null) mListener!!.onSerialConnect()
    }

    private fun onSerialConnect(service: UUID, characteristic: UUID) {
        if (mListener != null) mListener!!.onSerialConnect(service, characteristic)
    }

    private fun onSerialConnectError(e: Exception) {
        mCanceled = true
        if (mListener != null) mListener!!.onSerialConnectError(e)
    }

    private fun onSerialDisconnect() {
        if (mListener != null) mListener!!.onSerialDisconnect()
    }

    private fun onSerialRead(data: ByteArray, service: UUID, characteristic: UUID) {
        if (mListener != null) mListener!!.onSerialRead(data, service, characteristic)
    }

    private fun onBatteryEvent(data: ByteArray) {
        val level = data[0]
        if (mListener != null) mListener!!.onBatteryEvent(level)
    }

    private fun onSerialIoError(e: Exception) {
        mWritePending = false
        mCanceled = true
        if (mListener != null) mListener!!.onSerialIoError(e)
    }

    companion object {
        private val TAG = BleSerialSocket::class.java.name
        private val BLUETOOTH_LE_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val BLUETOOTH_LE_BATTERY_SERVICE =
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BLUETOOTH_LE_BATTERY_CHARACTERISTIC =
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        private const val MAX_MTU =
            512 // BLE standard does not limit, some BLE 4.2 devices support 251, various source say that Android has max 512
        private const val DEFAULT_MTU = 23
    }
}