package com.openmobl.pttDriver.bt

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.util.Log
import com.openmobl.pttDriver.Constants
import java.io.IOException
import java.security.InvalidParameterException
import java.util.*
import java.util.concurrent.Executors

class SppSerialSocket(context: Context, device: BluetoothDevice?) : Runnable, SerialSocket {
    private val mDisconnectBroadcastReceiver: BroadcastReceiver
    private val mReconnectBroadcastReceiver: BroadcastReceiver
    private val mContext: Context
    private var mListener: SerialListener? = null
    private val mDevice: BluetoothDevice?
    private var mSocket: BluetoothSocket? = null
    private var mConnected = false

    init {
        if (context is Activity) throw InvalidParameterException("expected non UI context")
        mContext = context
        mDevice = device
        mDisconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                disconnect(true)
            }
        }
        mReconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val oldListener = mListener
                disconnect(true)
                try {
                    connect(oldListener)
                } catch (e: IOException) {
                    Log.d(TAG, "reconnect failed")
                    e.printStackTrace()
                }
            }
        }
    }

    override val name: String?
        get() = if (mDevice!!.name != null) mDevice.name else mDevice.address
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
    }

    private fun unregisterReceivers() {
        try {
            mContext.unregisterReceiver(mDisconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
        try {
            mContext.unregisterReceiver(mReconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }
    }

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    @Throws(IOException::class)
    override fun connect(listener: SerialListener?) {
        if (mConnected) throw IOException("already connected")
        mListener = listener
        registerReceivers()
        Executors.newSingleThreadExecutor().submit(this)
    }

    override fun disconnect() {
        disconnect(false)
    }

    override fun disconnect(silent: Boolean) {
        //mListener = null; // ignore remaining data and errors
        mConnected = false
        if (mSocket != null) {
            try {
                mSocket!!.close()
            } catch (ignored: Exception) {
            }
            mSocket = null
        }
        unregisterReceivers()
        if (!silent && mListener != null) mListener!!.onSerialDisconnect()
        //mListener = null; // ignore remaining data and errors
    }

    @Throws(IOException::class)
    override fun write(data: ByteArray) {
        if (!mConnected) throw IOException("not connected")
        mSocket!!.outputStream.write(data)
    }

    override fun run() { // connect & read
        var sendIoErrorException: Exception? = null
        try {
            mSocket = mDevice!!.createRfcommSocketToServiceRecord(BLUETOOTH_SPP)
            mSocket.connect()
            if (mListener != null) mListener!!.onSerialConnect()
        } catch (e: Exception) {
            if (mListener != null) mListener!!.onSerialConnectError(e)
            try {
                mSocket!!.close()
            } catch (ignored: Exception) {
            }
            mSocket = null
            return
        }
        mConnected = true
        try {
            val buffer = ByteArray(1024)
            var len: Int
            while (mConnected) {
                len = mSocket.getInputStream().read(buffer)
                val data = Arrays.copyOf(buffer, len)
                if (mListener != null) mListener!!.onSerialRead(data, null, null)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Exception in SPP run loop: $e")
            e.printStackTrace()
            sendIoErrorException = e
        }
        mConnected = false
        if (sendIoErrorException != null && mListener != null) mListener!!.onSerialIoError(
            sendIoErrorException
        )
        try {
            mSocket.close()
        } catch (ignored: Exception) {
        }
        mSocket = null
    }

    companion object {
        private val TAG = SppSerialSocket::class.java.name
        private val BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}