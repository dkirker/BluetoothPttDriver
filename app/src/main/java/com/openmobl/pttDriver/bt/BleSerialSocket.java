package com.openmobl.pttDriver.bt;

import com.openmobl.pttDriver.Constants;
import com.openmobl.pttDriver.R;
import com.openmobl.pttDriver.utils.TextUtil;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * wrap BLE communication into socket like class
 *   - connect, disconnect and write as methods,
 *   - read + status is returned by SerialListener
 */
public class BleSerialSocket extends BluetoothGattCallback implements SerialSocket {
    private static final String TAG = BleSerialSocket.class.getName();

    private static final UUID BLUETOOTH_LE_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final UUID BLUETOOTH_LE_BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    private static final UUID BLUETOOTH_LE_BATTERY_CHARACTERISTIC = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");

    private static final int MAX_MTU = 512; // BLE standard does not limit, some BLE 4.2 devices support 251, various source say that Android has max 512
    private static final int DEFAULT_MTU = 23;

    private static class WriteBufferItem {
        private final byte[] mData;
        private final BluetoothGattCharacteristic mCharacteristic;

        public WriteBufferItem(byte[] data, BluetoothGattCharacteristic characteristic) {
            mData = data;
            mCharacteristic = characteristic;
        }
        public byte[] getData() { return mData; }
        public BluetoothGattCharacteristic getBtCharacteristic() { return mCharacteristic; }
    }

    private final ArrayList<WriteBufferItem> mWriteBuffer;
    private final ArrayList<WriteBufferItem> mCcdQueue;
    private final IntentFilter mPairingIntentFilter;
    private final BroadcastReceiver mPairingBroadcastReceiver;
    private final BroadcastReceiver mDisconnectBroadcastReceiver;
    private final BroadcastReceiver mReconnectBroadcastReceiver;

    private final Context mContext;
    private SerialListener mListener;
    private BleDeviceDelegate mDelegate;
    private BluetoothDevice mDevice;
    private BluetoothGatt mGatt;

    private boolean mWritePending;
    private boolean mCanceled;
    private boolean mConnected;
    private int payloadSize = DEFAULT_MTU-3;

    public BleSerialSocket(Context context, BluetoothDevice device) {
        Log.d(TAG, "BleSerialSocket");
        if(context instanceof Activity)
            throw new InvalidParameterException("expected non UI context");
        mContext = context;
        mDevice = device;
        mWriteBuffer = new ArrayList<>();
        mCcdQueue = new ArrayList<>();
        mPairingIntentFilter = new IntentFilter();
        mPairingIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mPairingIntentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        mPairingBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onPairingBroadcastReceive(context, intent);
            }
        };
        mDisconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //if(mListener != null)
                //    mListener.onSerialIoError(new IOException("background disconnect"));
                disconnect(true); // disconnect now, else would be queued until UI re-attached
            }
        };
        mReconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //if(mListener != null)
                //    mListener.onSerialIoError(new IOException("background reconnect"));
                SerialListener oldListener = mListener;
                disconnect(true); // disconnect now, else would be queued until UI re-attached
                try {
                    connect(oldListener);
                } catch (IOException e) {
                    Log.d(TAG, "reconnect failed");
                    e.printStackTrace();
                }
            }
        };
    }

    public BleSerialSocket(Context context, BluetoothDevice device, BleDeviceDelegate deviceDelegate) {
        this(context, device);
        mDelegate = deviceDelegate;
    }

    public void setDeviceDelegate(BleDeviceDelegate deviceDelegate) {
        mDelegate = deviceDelegate;
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
        mContext.registerReceiver(mPairingBroadcastReceiver, mPairingIntentFilter);
    }

    private void unregisterReceivers() {
        try {
            mContext.unregisterReceiver(mPairingBroadcastReceiver);
        } catch (Exception ignored) {
        }
        try {
            mContext.unregisterReceiver(mDisconnectBroadcastReceiver);
        } catch (Exception ignored) {
        }
        try {
            mContext.unregisterReceiver(mReconnectBroadcastReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void disconnect() {
        disconnect(false);
    }

    @Override
    public void disconnect(boolean silent) {
        Log.d(TAG, "disconnect");
        //mListener = null; // ignore remaining data and errors
        mDevice = null;
        mCanceled = true;
        synchronized (mWriteBuffer) {
            mWritePending = false;
            mWriteBuffer.clear();
        }
        if (mDelegate != null)
            mDelegate.disconnect();
        if (mGatt != null) {
            try {
                Log.d(TAG, "gatt.disconnect");
                mGatt.disconnect();
                Log.d(TAG, "gatt.close");
                mGatt.close();
            } catch (Exception e) {
                Log.d(TAG, "Exception while disconnecting and cleaning up: " + e);
                e.printStackTrace();
            }
            mGatt = null;
        }
        mConnected = false;

        unregisterReceivers();

        if (!silent && mListener != null)
            onSerialDisconnect();
        //mListener = null; // ignore remaining data and errors
    }

    /**
     * connect-success and most connect-errors are returned asynchronously to listener
     */
    @Override
    public void connect(SerialListener listener) throws IOException {
        if (mConnected || mGatt != null)
            throw new IOException("already connected");
        mCanceled = false;
        mListener = listener;

        unregisterReceivers();
        registerReceivers();

        Log.d(TAG, "connect "+mDevice);
        if (Build.VERSION.SDK_INT < 23) {
            Log.d(TAG, "connectGatt");
            mGatt = mDevice.connectGatt(mContext, false, this);
        } else {
            Log.d(TAG, "connectGatt,LE");
            mGatt = mDevice.connectGatt(mContext, false, this, BluetoothDevice.TRANSPORT_LE);
        }
        if (mGatt == null)
            throw new IOException("connectGatt failed");
        // continues asynchronously in onPairingBroadcastReceive() and onConnectionStateChange()
    }

    private void onPairingBroadcastReceive(Context context, Intent intent) {
        // for ARM Mbed, Microbit, ... use pairing from Android bluetooth settings
        // for HM10-clone, ... pairing is initiated here
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null || !device.equals(mDevice))
            return;
        switch (intent.getAction()) {
            case BluetoothDevice.ACTION_PAIRING_REQUEST:
                final int pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);
                Log.d(TAG, "pairing request " + pairingVariant);
                onSerialConnectError(new IOException(context.getString(R.string.pairing_request)));
                // pairing dialog brings app to background (onPause), but it is still partly visible (no onStop), so there is no automatic disconnect()
                break;
            case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
                Log.d(TAG, "bond state " + previousBondState + "->" + bondState);
                break;
            default:
                Log.d(TAG, "unknown broadcast " + intent.getAction());
                break;
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.v(TAG, "Received onConnectionStateChange: " + status + ", " + newState);

        // status directly taken from gat_api.h, e.g. 133=0x85=GATT_ERROR ~= timeout
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.d(TAG,"connect status "+status+", discoverServices");
            if (!gatt.discoverServices())
                onSerialConnectError(new IOException("discoverServices failed"));
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            if (mConnected)
                onSerialIoError(new IOException("gatt status " + status));
            else
                onSerialConnectError(new IOException("gatt status " + status));
        } else {
            Log.d(TAG, "unknown connect state "+newState+" "+status);
        }
        // continues asynchronously in onServicesDiscovered()
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.d(TAG, "servicesDiscovered, status " + status);
        if (mCanceled)
            return;
        connectCharacteristics1(gatt);
    }

    private void connectCharacteristics1(BluetoothGatt gatt) {
        boolean sync = true;
        mWritePending = false;

        if (mDelegate != null) {
            int syncCount = 0;

            for (BluetoothGattService gattService : gatt.getServices()) {
                Log.d(TAG, "gattService = " + gattService.getUuid());
                UUID serviceUUID = gattService.getUuid();

                if (mDelegate.containsReadService(serviceUUID)) {
                    List<UUID> characteristics = mDelegate.getReadCharacteristics(serviceUUID);

                    Log.v(TAG, "Setting GATT interface for read service " + serviceUUID);

                    for (UUID characteristic : characteristics) {
                        syncCount++;

                        Log.v(TAG, "Setting GATT interface for read characteristic " + characteristic);

                        mDelegate.setReadCharacteristic(serviceUUID, characteristic, gattService.getCharacteristic(characteristic));
                    }
                }
                if (mDelegate.containsWriteService(serviceUUID)) {
                    List<UUID> characteristics = mDelegate.getWriteCharacteristics(serviceUUID);

                    Log.v(TAG, "Setting GATT interface for write service " + serviceUUID);

                    for (UUID characteristic : characteristics) {
                        syncCount++;

                        Log.v(TAG, "Setting GATT interface for write characteristic " + characteristic);

                        mDelegate.setWriteCharacteristic(serviceUUID, characteristic, gattService.getCharacteristic(characteristic));
                    }
                }
                if (serviceUUID.equals(BLUETOOTH_LE_BATTERY_SERVICE)) {
                    syncCount++;

                    Log.v(TAG, "Setting GATT interface for battery service " + BLUETOOTH_LE_BATTERY_SERVICE);

                    mDelegate.addReadService(BLUETOOTH_LE_BATTERY_SERVICE);
                    mDelegate.addReadCharacteristic(BLUETOOTH_LE_BATTERY_SERVICE, BLUETOOTH_LE_BATTERY_CHARACTERISTIC);

                    Log.v(TAG, "Setting GATT interface for battery characteristic " + BLUETOOTH_LE_BATTERY_CHARACTERISTIC);

                    mDelegate.setReadCharacteristic(serviceUUID, BLUETOOTH_LE_BATTERY_CHARACTERISTIC, gattService.getCharacteristic(BLUETOOTH_LE_BATTERY_CHARACTERISTIC));
                }
            }

            sync = (syncCount > 0);
        }
        if (mCanceled)
            return;
        if (mDelegate == null || !sync) {
            Log.d(TAG, "No serial profile found (mDelegate == null: " + (mDelegate == null) + ", !sync = " + !sync + ")");

            for (BluetoothGattService gattService : gatt.getServices()) {
                Log.d(TAG, "service "+gattService.getUuid());
                for(BluetoothGattCharacteristic characteristic : gattService.getCharacteristics())
                    Log.d(TAG, "characteristic " + characteristic.getUuid());
            }
            onSerialConnectError(new IOException("no serial profile found"));
            return;
        }
        connectCharacteristics2(gatt);
    }

    private void connectCharacteristics2(BluetoothGatt gatt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.d(TAG, "request max MTU");
            if (!gatt.requestMtu(MAX_MTU))
                onSerialConnectError(new IOException("request MTU failed"));
            // continues asynchronously in onMtuChanged
        } else {
            connectCharacteristics3(gatt);
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        Log.d(TAG,"mtu size "+mtu+", status="+status);
        if (status ==  BluetoothGatt.GATT_SUCCESS) {
            payloadSize = mtu - 3;
            Log.d(TAG, "payload size "+payloadSize);
        }
        connectCharacteristics3(gatt);
    }

    private void connectCharacteristics3(BluetoothGatt gatt) {
        for (BluetoothGattCharacteristic characteristic : mDelegate.getAllWriteCharacteristics()) {
            try {
                int writeProperties = characteristic.getProperties();
                if ((writeProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE +     // Microbit,HM10-clone have WRITE
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) { // HM10,TI uart,Telit have only WRITE_NO_RESPONSE
                    onSerialConnectError(new IOException("write characteristic " + characteristic.getUuid() + " not writable"));
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Exception setting up write characteristic");
            }
        }

        for (BluetoothGattCharacteristic characteristic : mDelegate.getAllReadCharacteristics()) {
            try {
                if (!gatt.setCharacteristicNotification(characteristic, true)) {
                    onSerialConnectError(new IOException("no notification for read characteristic " + characteristic.getUuid()));
                    return;
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

                Log.d(TAG, "Add read characteristic descriptor to queue for " + characteristic.getUuid());

                mCcdQueue.add(new WriteBufferItem(valueToSend, characteristic));

                // continues asynchronously in onDescriptorWrite()
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Exception setting up read characteristic");
            }
        }

        processCcdQueueItem(gatt);
    }

    // TODO: There are edge cases around this code right now...
    private void processCcdQueueItem(BluetoothGatt gatt) {
        if (mCcdQueue.size() > 0) {
            try {
                WriteBufferItem queueItem = mCcdQueue.remove(0);
                BluetoothGattCharacteristic characteristic = queueItem.getBtCharacteristic();

                BluetoothGattDescriptor readDescriptor = characteristic.getDescriptor(BLUETOOTH_LE_CCCD);

                if (readDescriptor == null) {
                    Log.d(TAG, "no CCCD descriptor for read characteristic " + characteristic.getUuid());
                    //onSerialConnectError(new IOException("no CCCD descriptor for read characteristic " + characteristic.getUuid()));
                    processCcdQueueItem(gatt);
                    return;
                }

                readDescriptor.setValue(queueItem.getData());

                if (!gatt.writeDescriptor(readDescriptor)) {
                    Log.d(TAG, "read characteristic CCCD descriptor not writable " + characteristic.getUuid());
                    //onSerialConnectError(new IOException("read characteristic CCCD descriptor not writable " + characteristic.getUuid()));
                    processCcdQueueItem(gatt);
                    return;
                }
            } catch (Exception e) {
                Log.d(TAG, "Received exception in processCcdQueueItem: " + e);
                e.printStackTrace();
                processCcdQueueItem(gatt);
            }
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        mDelegate.onDescriptorWrite(gatt, descriptor, status);
        if (mCanceled)
            return;
        if (mDelegate.containsReadCharacteristic(descriptor.getCharacteristic().getService().getUuid(),
                descriptor.getCharacteristic().getUuid())) {
            Log.d(TAG,"writing read characteristic descriptor for " + descriptor.getCharacteristic().getUuid() + "finished, status="+status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onSerialConnectError(new IOException("write descriptor failed"));
            } else {
                // onCharacteristicChanged with incoming data can happen after writeDescriptor(ENABLE_INDICATION/NOTIFICATION)
                // before confirmed by this method, so receive data can be shown before device is shown as 'Connected'.
                /*onSerialConnect(descriptor.getCharacteristic().getService().getUuid(),
                        descriptor.getCharacteristic().getUuid());
                mConnected = true;
                Log.d(TAG, "connected");*/

                if (mCcdQueue.size() > 0) {
                    processCcdQueueItem(gatt);
                } else {
                    onSerialConnect();
                    mConnected = true;
                    Log.d(TAG, "connected");
                }
            }
        }
    }

    /*
     * read
     */
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (mCanceled)
            return;
        mDelegate.onCharacteristicChanged(gatt, characteristic);
        if (mCanceled)
            return;
        if (BLUETOOTH_LE_BATTERY_CHARACTERISTIC.equals(characteristic.getUuid()) &&
            BLUETOOTH_LE_BATTERY_SERVICE.equals(characteristic.getService().getUuid())) {
            byte[] data = characteristic.getValue();

            onBatteryEvent(data);
        } else if (mDelegate.containsReadCharacteristic(characteristic.getService().getUuid(),
                    characteristic.getUuid())) { // NOPMD - test object identity
            byte[] data = characteristic.getValue();

            Log.d(TAG,"read (onCharacteristicChanged), len = " + data.length + ", data = " + TextUtil.toHexString(data));

            onSerialRead(data, characteristic.getService().getUuid(), characteristic.getUuid());
        }
    }

    /*
     * write
     */
    @Override
    public void write(byte[] data) throws IOException {
        if (mCanceled || !mConnected || mDelegate == null)
            throw new IOException("not connected");

        List<UUID> services = mDelegate.getWriteServices();
        if (services.size() > 0) {
            UUID service = services.get(0);
            List<UUID> characteristics = mDelegate.getWriteCharacteristics(service);

            if (characteristics.size() > 0) {
                write(data, service, characteristics.get(0));
            } else {
                throw new IOException("Could not write to device - no write characteristics");
            }
        } else {
            throw new IOException("Could not write to device - no write services");
        }
    }

    public void write(byte[] data, UUID service, UUID characteristic) throws IOException {
        if (mCanceled || !mConnected || mDelegate == null)
            throw new IOException("not connected");
        if (service == null || characteristic == null)
            throw new IOException("cannot write to device");

        write(data, mDelegate.getWriteCharacteristic(service, characteristic));
    }

    public void write(byte[] data, BluetoothGattCharacteristic characteristic) throws IOException {
        if (mCanceled || !mConnected || mDelegate == null)
            throw new IOException("not connected");
        if (characteristic == null)
            throw new IOException("cannot write to device");

        byte[] data0;

        synchronized (mWriteBuffer) {
            if (data.length <= payloadSize) {
                data0 = data;
            } else {
                data0 = Arrays.copyOfRange(data, 0, payloadSize);
            }
            if (!mWritePending && mWriteBuffer.isEmpty() && mDelegate.canWrite()) {
                mWritePending = true;
            } else {
                mWriteBuffer.add(new WriteBufferItem(data0, characteristic));
                Log.d(TAG,"write queued, len="+data0.length);
                data0 = null;
            }
            if (data.length > payloadSize) {
                for (int i=1; i<(data.length+payloadSize-1)/payloadSize; i++) {
                    int from = i*payloadSize;
                    int to = Math.min(from+payloadSize, data.length);
                    mWriteBuffer.add(new WriteBufferItem(Arrays.copyOfRange(data, from, to), characteristic));
                    Log.d(TAG,"write queued, len="+(to-from));
                }
            }
        }

        if (data0 != null) {
            characteristic.setValue(data0);
            if (!mGatt.writeCharacteristic(characteristic)) {
                onSerialIoError(new IOException("write failed"));
            } else {
                Log.d(TAG,"write started, len="+data0.length);
            }
        }
        // continues asynchronously in onCharacteristicWrite()
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (mCanceled || !mConnected)
            return;
        if (status != BluetoothGatt.GATT_SUCCESS) {
            onSerialIoError(new IOException("write failed"));
            return;
        }
        mDelegate.onCharacteristicWrite(gatt, characteristic, status);
        if (mCanceled)
            return;
        if (mDelegate.containsWriteCharacteristic(characteristic.getService().getUuid(), characteristic.getUuid())) { // NOPMD - test object identity
            Log.d(TAG,"write finished, status="+status);
            writeNext();
        }
    }

    private void writeNext() {
        final byte[] data;
        BluetoothGattCharacteristic characteristic;
        synchronized (mWriteBuffer) {
            if (!mWriteBuffer.isEmpty() && mDelegate.canWrite()) {
                mWritePending = true;
                WriteBufferItem packet = mWriteBuffer.remove(0);
                data = packet.getData();
                characteristic = packet.getBtCharacteristic();
            } else {
                mWritePending = false;
                data = null;
                characteristic = null;
            }
        }
        if(data != null && characteristic != null) {
            characteristic.setValue(data);
            if (!mGatt.writeCharacteristic(characteristic)) {
                onSerialIoError(new IOException("write failed"));
            } else {
                Log.d(TAG,"write started, len="+data.length);
            }
        }
    }

    /**
     * SerialListener
     */
    private void onSerialConnect() {
        if (mListener != null)
            mListener.onSerialConnect();
    }

    private void onSerialConnect(UUID service, UUID characteristic) {
        if (mListener != null)
            mListener.onSerialConnect(service, characteristic);
    }

    private void onSerialConnectError(Exception e) {
        mCanceled = true;
        if (mListener != null)
            mListener.onSerialConnectError(e);
    }

    private void onSerialDisconnect() {
        if (mListener != null)
            mListener.onSerialDisconnect();
    }

    private void onSerialRead(byte[] data, UUID service, UUID characteristic) {
        if (mListener != null)
            mListener.onSerialRead(data, service, characteristic);
    }

    private void onBatteryEvent(byte[] data) {
        byte level = data[0];

        if (mListener != null)
            mListener.onBatteryEvent(level);
    }

    private void onSerialIoError(Exception e) {
        mWritePending = false;
        mCanceled = true;
        if (mListener != null)
            mListener.onSerialIoError(e);
    }
}
