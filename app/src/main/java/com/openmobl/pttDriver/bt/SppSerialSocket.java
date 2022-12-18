package com.openmobl.pttDriver.bt;

import com.openmobl.pttDriver.Constants;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;

public class SppSerialSocket implements Runnable, SerialSocket {
    private static final String TAG = SppSerialSocket.class.getName();

    private static final UUID BLUETOOTH_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BroadcastReceiver mDisconnectBroadcastReceiver;
    private final BroadcastReceiver mReconnectBroadcastReceiver;

    private final Context mContext;
    private SerialListener mListener;
    private final BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
    private boolean mConnected;

    public SppSerialSocket(Context context, BluetoothDevice device) {
        if(context instanceof Activity)
            throw new InvalidParameterException("expected non UI context");
        mContext = context;
        mDevice = device;
        mDisconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                disconnect(true);
            }
        };
        mReconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SerialListener oldListener = mListener;
                disconnect(true);
                try {
                    connect(oldListener);
                } catch (IOException e) {
                    Log.d(TAG, "reconnect failed");
                    e.printStackTrace();
                }
            }
        };
    }

    @Override
    public String getName() {
        return mDevice.getName() != null ? mDevice.getName() : mDevice.getAddress();
    }

    @Override
    public String getAddress() {
        return mDevice.getAddress();
    }

    private void registerReceivers() {
        mContext.registerReceiver(mDisconnectBroadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_DISCONNECT));
        mContext.registerReceiver(mReconnectBroadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_RECONNECT));
    }

    private void unregisterReceivers() {
        try {
            mContext.unregisterReceiver(mDisconnectBroadcastReceiver);
        } catch (Exception ignored) {
        }
        try {
            mContext.unregisterReceiver(mReconnectBroadcastReceiver);
        } catch (Exception ignored) {
        }
    }

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    @Override
    public void connect(SerialListener listener) throws IOException {
        if (mConnected)
            throw new IOException("already connected");

        mListener = listener;

        registerReceivers();

        Executors.newSingleThreadExecutor().submit(this);
    }

    @Override
    public void disconnect() {
        disconnect(false);
    }

    @Override
    public void disconnect(boolean silent) {
        //mListener = null; // ignore remaining data and errors
        mConnected = false;
        if(mSocket != null) {
            try {
                mSocket.close();
            } catch (Exception ignored) {
            }
            mSocket = null;
        }

        unregisterReceivers();

        if (!silent && mListener != null)
            mListener.onSerialDisconnect();
        //mListener = null; // ignore remaining data and errors
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (!mConnected)
            throw new IOException("not connected");
        mSocket.getOutputStream().write(data);
    }

    @Override
    public void run() { // connect & read
        Exception sendIoErrorException = null;

        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(BLUETOOTH_SPP);
            mSocket.connect();

            if(mListener != null)
                mListener.onSerialConnect();
        } catch (Exception e) {
            if(mListener != null)
                mListener.onSerialConnectError(e);

            try {
                mSocket.close();
            } catch (Exception ignored) {
            }
            mSocket = null;
            return;
        }

        mConnected = true;

        try {
            byte[] buffer = new byte[1024];
            int len;

            while (mConnected) {
                len = mSocket.getInputStream().read(buffer);

                byte[] data = Arrays.copyOf(buffer, len);

                if (mListener != null)
                    mListener.onSerialRead(data, null, null);
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception in SPP run loop: " + e);
            e.printStackTrace();

            sendIoErrorException = e;
        }

        mConnected = false;
        if (sendIoErrorException != null && mListener != null)
            mListener.onSerialIoError(sendIoErrorException);
        try {
            mSocket.close();
        } catch (Exception ignored) {
        }
        mSocket = null;
    }

}
