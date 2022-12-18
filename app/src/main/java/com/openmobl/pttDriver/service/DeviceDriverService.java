package com.openmobl.pttDriver.service;

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;

import com.openmobl.pttDriver.Constants;
import com.openmobl.pttDriver.R;
import com.openmobl.pttDriver.bt.HfpSerialSocket;
import com.openmobl.pttDriver.bt.hfp.AtCommandResult;
import com.openmobl.pttDriver.model.PttDriver;
import com.openmobl.pttDriver.bt.BleDeviceDelegate;
import com.openmobl.pttDriver.bt.BleSerialSocket;
import com.openmobl.pttDriver.bt.SerialListener;
import com.openmobl.pttDriver.bt.SerialSocket;
import com.openmobl.pttDriver.bt.SppSerialSocket;
import com.openmobl.pttDriver.utils.SoundUtils;
import com.openmobl.pttDriver.utils.TextUtil;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * show list of BLE devices
 */
public class DeviceDriverService extends Service implements IDeviceDriverService, SerialListener {
    private static final String TAG = DeviceDriverService.class.getName();

    // If we stay connected for more than two minutes, we can reset the reset count
    private static final long RECONNECT_COUNT_RESET_MILLI = 120000;
    // If we try to reconnect more than this many times reset the count which resets the back-off delay
    private static final long RECONNECT_COUNT_RESET_AFTER = 60;

    public interface DeviceStatusListener {
        void onStatusMessageUpdate(String message);

        void onConnected();
        void onDisconnected();

        void onBatteryEvent(byte level);
    }

    public enum Connected { False, Pending, True }

    public static class DeviceDriverBinder extends Binder {
        private final DeviceDriverService mService;

        private DeviceDriverBinder(DeviceDriverService service) {
            mService = service;
        }

        public IDeviceDriverService getService() {
            return mService;
        }
    }

    private Connected mConnected = Connected.False;
    private boolean mEnabledSent = false;
    private long mReconnectCount;
    private Date mLastReconnectAttempt;
    private String mLastIntentSent;
    private Date mLastIntentSentTime;

    private SerialSocket mSocket;

    private BluetoothDevice mPttDevice;
    private BluetoothDevice mPttWatchForDevice;
    private PttDriver mPttDriver;
    private BleDeviceDelegate mPttDeviceDelegate;
    private boolean mConnectOnComplete;
    private boolean mAutomaticallyReconnect;
    private int mPttDownKeyDelay;
    private boolean mPttDownKeyDelayOverride;
    private NotificationCompat.Builder mNotificationBuilder;
    private Map<BluetoothDevice, String> mConnectionState;
    private final BroadcastReceiver mDeviceConnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            mConnectionState.put(device, action);


            if (mPttDevice != null && device.getAddress().equals(mPttDevice.getAddress())) {
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    Log.v(TAG, "Our device " + device.getAddress() + " has reconnected, reconnect to service");
                    if (mConnected != Connected.True)
                        reconnectAutomatically();
                } /*else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    Log.v(TAG, "Our device " + device.getAddress() + " has disconnected, clean up");
                    disconnect();
                }else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
                    Log.v(TAG, "Our device " + device.getAddress() + " has requested to disconnect");
                    //Device is about to disconnect
                    //disconnect();
                }*/
            }
        }
    };

    private Handler mReconnectTimerHandler;
    private final Runnable mReconnectCallback = new Runnable() {
        @Override
        public void run() {
            Log.v(TAG, "Try reconnect to device");
            connect();
        }
    };

    private DeviceStatusListener mStatusListener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");

        mReconnectTimerHandler = new Handler(getMainLooper());

        createNotification(getString(R.string.status_disconnected));

        mConnectOnComplete = false;
        mAutomaticallyReconnect = false;
        mPttDownKeyDelay = 0;
        mPttDownKeyDelayOverride = false;

        mConnectionState = new HashMap<>();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mDeviceConnectReceiver, filter);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");
        if (mConnected != Connected.False)
            disconnect();

        try {
            unregisterReceiver(mDeviceConnectReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        cancelNotification();

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        return new DeviceDriverBinder(this);
    }

    /*
        PTT Device parameters
     */
    @Override
    public void setPttDevice(BluetoothDevice device) {
        Log.v(TAG, "setPttDevice");

        mPttDevice = device;
        checkConnectOnComplete();
    }
    // TODO: A separate device to watch for connecting
    @Override
    public void setPttWatchForDevice(BluetoothDevice device) {
        mPttWatchForDevice = device;
        //checkConnectOnComplete();
    }
    @Override
    public void setPttWatchForDevice(String name) {
        try {
            //BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bluetoothAdapter = manager.getAdapter();
            //BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : devices) {
                if (device.getName().contains(name)) {
                    setPttWatchForDevice(device);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void setPttDriver(PttDriver driver) {
        Log.v(TAG, "setPttDriver");

        mPttDriver = driver;
        createDeviceDelegate();

        if (mPttWatchForDevice != null && mPttDriver.getWatchForDeviceName() != null) {
            setPttWatchForDevice(mPttDriver.getWatchForDeviceName());
        }

        checkConnectOnComplete();
    }
    private void createDeviceDelegate() {
        if (mPttDriver != null && mPttDriver.isValid()) {
            if (mPttDriver.getType() == PttDriver.ConnectionType.BLE && mPttDriver.getReadObj().getCharacteristic() == null) {
                mPttDeviceDelegate = new BleDeviceDelegate();
                Map<UUID, PttDriver.IntentMap> characteristicMaps = mPttDriver.getReadObj().getCharacteristicIntentMaps();
                UUID readServiceUUID = mPttDriver.getReadObj().getService();

                mPttDeviceDelegate.addReadService(readServiceUUID);
                for (Map.Entry<UUID, PttDriver.IntentMap> mapping : characteristicMaps.entrySet()) {
                    PttDriver.IntentMap characteristics = mapping.getValue();

                    mPttDeviceDelegate.addReadCharacteristic(readServiceUUID, mapping.getKey());
                }

                if (mPttDriver.getWriteObj() != null && mPttDriver.getWriteObj().getService() != null) {
                    mPttDeviceDelegate.addWriteCharacteristic(mPttDriver.getWriteObj().getService(),
                            mPttDriver.getWriteObj().getCharacteristic());
                }
            } else if (mPttDriver.getType() == PttDriver.ConnectionType.BLE_SERIAL ||
                    (mPttDriver.getType() == PttDriver.ConnectionType.BLE && mPttDriver.getReadObj().getCharacteristic() != null)) {
                mPttDeviceDelegate = new BleDeviceDelegate();

                mPttDeviceDelegate.addReadCharacteristic(mPttDriver.getReadObj().getService(),
                        mPttDriver.getReadObj().getCharacteristic());
                if (mPttDriver.getWriteObj() != null && mPttDriver.getWriteObj().getService() != null) {
                    mPttDeviceDelegate.addWriteCharacteristic(mPttDriver.getWriteObj().getService(),
                            mPttDriver.getWriteObj().getCharacteristic());
                }
            }
        }
    }
    // Connect on complete signals the device driver to connect to the device when all necessary fields
    // have been set and are valid.
    @Override
    public void setConnectOnComplete(boolean connectOnComplete) {
        mConnectOnComplete = connectOnComplete;

        checkConnectOnComplete();
    }
    @Override
    public boolean getConnectOnComplete() { return mConnectOnComplete; }

    private void checkConnectOnComplete() {
        if (mConnectOnComplete &&
                mPttDevice != null && mPttDriver != null &&
                mPttDriver.isValid()) {
            // Don't connect if already connected...
            connect();
        }
    }

    @Override
    public void setAutomaticallyReconnect(boolean autoReconnect) {
        mAutomaticallyReconnect = autoReconnect;
    }
    @Override
    public boolean getAutomaticallyReconnect() { return mAutomaticallyReconnect; }

    @Override
    public void setPttDownKeyDelay(int delay) {
        mPttDownKeyDelay = delay;
        mPttDownKeyDelayOverride = true;
    }
    @Override
    public int getPttDownKeyDelay() { return mPttDownKeyDelay; }

    @Override
    public void registerStatusListener(DeviceStatusListener statusListener) {
        mStatusListener = statusListener;
    }
    @Override
    public void unregisterStatusListener(DeviceStatusListener statusListener) {
        mStatusListener = null;
    }

    /*
        Serial API
     */
    public String getDeviceName() {
        if (mSocket != null) {
            return mSocket.getName();
        }
        return null;
    }

    public String getDeviceAddress() {
        if (mSocket != null) {
            return mSocket.getAddress();
        }
        return null;
    }

    @Override
    public Connected getConnected() {
        return mConnected;
    }

    @Override
    public void connect() {
        Log.v(TAG, "connect()");

        if (mConnected == Connected.False) {
            status(R.string.status_connecting_to_device);
            try {
                //BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                //BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                status(R.string.status_connecting);
                mConnected = Connected.Pending;

                switch (mPttDriver.getType()) {
                    case BLE:
                    case BLE_SERIAL:
                        mSocket = new BleSerialSocket(this, mPttDevice, mPttDeviceDelegate);
                        break;
                    case SPP:
                        mSocket = new SppSerialSocket(this, mPttDevice);
                        break;
                    case HFP:
                        mSocket = new HfpSerialSocket(this, mPttDevice);
                        break;
                    default:
                        return;
                }
                mSocket.connect(this);


                createNotification(mSocket != null ? getString(R.string.connecting_to_prefix) + " " + getDeviceName() : getString(R.string.background_service));
            } catch (Exception e) {
                Log.d(TAG, "Exception in connect(): " + e);
                onSerialConnectError(e);
            }
        } else {
            Log.d(TAG, "State is not disconnected -- " + mConnected);
        }
    }

    @Override
    public void disconnect() {
        status(R.string.status_disconnecting);
        mConnected = Connected.False;
        mEnabledSent = false;

        createNotification(getString(R.string.status_disconnected));

        if (mSocket != null) {
            mSocket.disconnect();
            mSocket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (mConnected == Connected.False)
            throw new IOException("not connected");
        mSocket.write(data);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        onSerialConnect(null, null);
    }

    @Override
    public void onSerialConnect(UUID service, UUID characteristic) {
        status(R.string.status_connected);
        mConnected = Connected.True;

        createNotification(mSocket != null ? getString(R.string.connected_to_prefix) + " " + getDeviceName() : getString(R.string.background_service));

        if (mStatusListener != null) {
            mStatusListener.onConnected();
        }

        if (!mEnabledSent) {
            final Handler handler = new Handler(getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    enablePtt();
                }
            }, 500);
        }
    }

    private void playSoundIfEnabled(int resId) {
        // TODO: Create a pref for this
        try {
            SoundUtils.playSoundResource(getApplicationContext(), resId, getDeviceAddress());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enablePtt() {
        status(R.string.status_enabling);

        if (mPttDriver.getWriteObj() == null || mPttDriver.getWriteObj().getStartCmdStr() == null ||
                mPttDriver.getWriteObj().getStartCmdStr().isEmpty()) {
            status(R.string.status_connected);

            mEnabledSent = true;

            playSoundIfEnabled(R.raw.sound_ptt_connected__hi);

            return;
        }

        try {
            if (mPttDriver.getWriteObj().getStartCmdStr() != null) {
                String startCmdStr = mPttDriver.getWriteObj().getStartCmdStr();
                //String eolStr = mPttDriver.getWriteObj().getEOL();

                byte[] startCmd = (mPttDriver.getWriteObj().getStartCmdStrType() == PttDriver.DataType.ASCII) ?
                        startCmdStr.getBytes() : TextUtil.fromHexString(startCmdStr);

                Log.v(TAG, "Sending startCmd: " + startCmdStr + " (" + TextUtil.toHexString(startCmd) + ")");

                /*if (eolStr != null) {
                    byte[] eol = TextUtil.fromHexString(eolStr);
                    key = (new String(data)).replace(new String(eol), "");
                }*/

                write(startCmd);
            } /*else {
                byte[] data = { 0x00 };

                write(data);
            }*/
        } catch (Exception e) {
            //if (!e.getMessage().equalsIgnoreCase("cannot write to device")) {
                onSerialIoError(e);
            //}
        }

        try {
            final Handler handler = new Handler(getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    status(R.string.status_connected);
                    // TODO: We really need to confirm we are connected
                    playSoundIfEnabled(R.raw.sound_ptt_connected__hi);
                }
            }, 500);

            mEnabledSent = true;
        } catch (Exception e) {
            //if (!e.getMessage().equalsIgnoreCase("cannot write to device")) {
                onSerialIoError(e);
            //}
        }
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status(getString(R.string.status_prefix_connection_failed) + " " + e.getMessage());

        mConnected = Connected.False;

        if (mStatusListener != null) {
            mStatusListener.onDisconnected();
        }

        disconnect();
        reconnectAutomatically();
    }

    @Override
    public void onSerialDisconnect() {
        status(R.string.status_disconnected);

        mEnabledSent = false;
        mConnected = Connected.False;

        createNotification(getString(R.string.status_disconnected));

        if (mStatusListener != null) {
            mStatusListener.onDisconnected();
        }

        reconnectAutomatically();
    }

    private static boolean internal_isConnected(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("isConnected", (Class[]) null);
            boolean connected = (boolean) m.invoke(device, (Object[]) null);

            Log.v(TAG, "internal_isConnected " + connected);

            return connected;
        } catch (Exception e) {
            Log.v(TAG, "internal_isConnected threw exception " + e);

            return false;
        }
    }

    private boolean deviceIsConnected(BluetoothDevice device) {
        String state = device != null ? mConnectionState.get(device) : null;

        Log.v(TAG, "deviceIsConnected bluetooth device state: " + state);

        boolean connected = BluetoothDevice.ACTION_ACL_CONNECTED.equals(state);

        Log.v(TAG, "deviceIsConnected is " + connected);

        return connected || (device != null && internal_isConnected(device));
    }

    private boolean deviceIsConnected(String macAddress) {
        BluetoothDevice device = null;

        for (BluetoothDevice dev : mConnectionState.keySet()) {
            if (macAddress.equals(dev.getAddress())) {
                device = dev;
            }
        }

        return deviceIsConnected(device);
    }

    private void reconnectAutomatically() {
        // If we are waiting on a reconnect attempt then bail out so that we aren't resetting our attempt,
        // or flooding the system.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mReconnectTimerHandler.hasCallbacks(mReconnectCallback)) {
            Log.d(TAG, "Reconnect attempt pending, don't schedule more");
            return;
        }

        if (getAutomaticallyReconnect()) {
            Log.d(TAG, "Check reconnect automatically");

            try {
                boolean shouldReconnect = deviceIsConnected(mPttDevice);
                Log.v(TAG, "shouldReconnect " + shouldReconnect);

                if (!shouldReconnect && mPttWatchForDevice != null) {
                    shouldReconnect = deviceIsConnected(mPttWatchForDevice);
                }

                Log.v(TAG, "shouldReconnect " + shouldReconnect);

                if (shouldReconnect) {
                    Date now = new Date();
                    if (mLastReconnectAttempt != null &&
                        now.getTime() - mLastReconnectAttempt.getTime() > RECONNECT_COUNT_RESET_MILLI) {
                        mReconnectCount = 0;
                    } else if (mReconnectCount > RECONNECT_COUNT_RESET_AFTER) {
                        mReconnectCount = 0;
                    }
                    status(R.string.status_reconnecting);
                    mReconnectCount++;
                    mLastReconnectAttempt = now;

                    Log.v(TAG, "Attempting reconnect in " + (1000 * mReconnectCount) + "ms");

                    mReconnectTimerHandler.postDelayed(mReconnectCallback, 1000 * mReconnectCount);
                }
            } catch (Exception e) {
                status(getString(R.string.status_prefix_failed_to_reconnect) + " " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void sendIntentInternal(final String intentName) {
        // Possibility that user pressed the key down and released it before the key down delay.
        // There might be an edge case here, so we *might* want to also include a timestamp with the
        // call to sendIntentInternal.
        if (!intentName.equals(mLastIntentSent)) {
            return;
        }
        try {
            Intent intent = new Intent();

            Log.d(TAG, "Sending intent: " + intentName);

            if (intentName.contains(":") && intentName.contains(",")) {
                String eventData = intentName.split(":")[1];
                String newName = intentName.split(":")[0];

                int keyCode = KeyEvent.keyCodeFromString(eventData.split(",")[0]);
                int keyAction = Integer.parseInt(eventData.split(",")[1]);

                Log.v(TAG, "Sending KeyEvent Intent " + newName + " keyCode: " +
                        keyCode + " keyAction: " + keyAction);

                KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(),
                                              SystemClock.uptimeMillis(),
                                              keyAction, keyCode, 0);

                intent.setAction(newName);
                intent.putExtra(Intent.EXTRA_KEY_EVENT, event);

                sendBroadcast(intent);
            } else {
                intent.setAction(intentName);

                sendBroadcast(intent);
            }
            // libsu and `am broadcast -a <intentName>` to support protected intents?
        } catch (Exception e) {
            Log.d(TAG, "Exception sending intent: " + e);
        }
    }

    private void sendIntent(final String intentName) {
        sendIntent(intentName, 0);
    }
    private void sendIntent(final String intentName, int delay) {
        Date now = new Date();

        if (mLastIntentSent != null && mPttDriver.getReadObj().getIntentsDeDuplicateNoTimeout().size() > 0 &&
            mLastIntentSent.equals(intentName) && mPttDriver.getReadObj().getIntentsDeDuplicateNoTimeout().contains(intentName)) {
            return;
        }

        if (mPttDriver.getReadObj().getIntentDeDuplicate() && mLastIntentSentTime != null &&
                mLastIntentSent != null && mLastIntentSent.equals(intentName) &&
                now.getTime() - mLastIntentSentTime.getTime() < mPttDriver.getReadObj().getIntentDeDuplicateTimeout()) {
            return;
        }

        mLastIntentSent = intentName;
        // We might consider accounting for the delay...
        mLastIntentSentTime = new Date();

        if (delay > 0) {
            final Handler handler = new Handler(getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendIntentInternal(intentName);
                }
            }, delay);
        } else {
            sendIntentInternal(intentName);
        }
    }

    @Override
    public void onSerialRead(byte[] data, UUID service, UUID characteristic) {
        String intentName = null;
        String key = (mPttDriver.getReadObj().getSerialDataType() == PttDriver.DataType.ASCII) ? new String(data) : TextUtil.toHexString(data);

        Log.v(TAG, "onSerialRead - data = " + TextUtil.toHexString(data) + ", serialDataType = " + mPttDriver.getReadObj().getSerialDataType() +
                        ", key = " + key);

        if (mPttDriver.getType() == PttDriver.ConnectionType.BLE_SERIAL ||
                mPttDriver.getType() == PttDriver.ConnectionType.SPP ||
                mPttDriver.getType() == PttDriver.ConnectionType.HFP ||
                (mPttDriver.getType() == PttDriver.ConnectionType.BLE && mPttDriver.getReadObj().getCharacteristic() != null)) {
            String eolStr = mPttDriver.getReadObj().getEOL();

            Log.v(TAG, "Read from intent map");

            if (eolStr != null) {
                byte[] eol = TextUtil.fromHexString(eolStr);
                key = (new String(data)).replace(new String(eol), "");
            }

            Log.d(TAG, "Received button press: " + key);

            PttDriver.IntentMap pttIntentMap = mPttDriver.getReadObj() != null ? mPttDriver.getReadObj().getIntentMap() : null;

            if (pttIntentMap != null && pttIntentMap.containsKey(key)) {
                intentName = pttIntentMap.get(key);
            }

            if (mPttDriver.getType() == PttDriver.ConnectionType.HFP) {
                // If we aren't doing anything with this data then pass it to the HFP engine
                if (intentName == null) {
                    ((HfpSerialSocket) mSocket).processAtCommands(key);
                } else { // Otherwise, acknowledge it
                    AtCommandResult result = new AtCommandResult(AtCommandResult.OK);

                    try {
                        write(result.toString().getBytes());
                    } catch (Exception e) {
                        Log.d(TAG, "Exception in sending AtCommandResult.OK");
                        e.printStackTrace();
                    }
                }
            }
        } else if (mPttDriver.getType() == PttDriver.ConnectionType.BLE) {
            Log.v(TAG, "Read from characteristic intent maps");

            if (key.length() > 0) {
                key = key.replaceAll("\\s", "");
                Log.d(TAG, "Received button press: " + key);

                Map<UUID, PttDriver.IntentMap> pttCharacteristicsIntentMap =
                        mPttDriver.getReadObj() != null ? mPttDriver.getReadObj().getCharacteristicIntentMaps() : null;

                if (pttCharacteristicsIntentMap != null && pttCharacteristicsIntentMap.containsKey(characteristic) &&
                        pttCharacteristicsIntentMap.get(characteristic) != null &&
                        pttCharacteristicsIntentMap.get(characteristic).containsKey(key)) {
                    intentName = pttCharacteristicsIntentMap.get(characteristic).get(key);
                }
            }
        }

        if (intentName != null) {
            int delay = 0;

            if (mPttDriver.getReadObj().getPttDownKeyIntent() != null &&
                intentName.equals(mPttDriver.getReadObj().getPttDownKeyIntent())) {
                // If the user did not override the ptt key down delay, check to see what the driver defines
                delay = mPttDownKeyDelayOverride ? mPttDownKeyDelay : mPttDriver.getReadObj().getDefaultPttDownKeyDelay();
            }

            sendIntent(intentName, delay);
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status(getString(R.string.status_prefix_connection_lost) + " " + e.getMessage());
        disconnect();

        mConnected = Connected.False;

        // Are we actually "disconnected"?
        createNotification(getString(R.string.status_disconnected));

        if (mStatusListener != null) {
            mStatusListener.onDisconnected();
        }

        reconnectAutomatically();
    }

    @Override
    public void onBatteryEvent(byte level) {
        if (mConnected != Connected.False) {
            createNotification(
                    (mSocket != null ? getString(R.string.connected_to_prefix) + " " + getDeviceName() : getString(R.string.background_service)) +
                            " - " + getString(R.string.battery_prefix) + " " + level + "%");

            if (mStatusListener != null) {
                mStatusListener.onBatteryEvent(level);
            }
        }
    }

    private void createNotification(String message) {
        String channelId = "";
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        boolean newNotif = false;

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(Constants.NOTIFICATION_CHANNEL, "Background service", NotificationManager.IMPORTANCE_LOW);
            nc.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }*/

        if (mNotificationBuilder == null) {
            newNotif = true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channelId = Constants.NOTIFICATION_CHANNEL;
                String channelName = getString(R.string.background_service);
                NotificationChannelCompat chan = new NotificationChannelCompat.Builder(channelId,
                        NotificationManagerCompat.IMPORTANCE_LOW)
                        .setName(channelName)
                        .build();
                manager.createNotificationChannel(chan);
            }
            mNotificationBuilder = new NotificationCompat.Builder(this, channelId);
        }

        Intent disconnectIntent = new Intent(Constants.INTENT_ACTION_DISCONNECT);
        Intent reconnectIntent = new Intent(Constants.INTENT_ACTION_RECONNECT);
        PendingIntent disconnectPendingIntent = PendingIntent.getBroadcast(this, 1,
                disconnectIntent, FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE);
        PendingIntent reconnectPendingIntent = PendingIntent.getBroadcast(this, 2,
                reconnectIntent, FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE);

        mNotificationBuilder.clearActions();
        if (mConnected != Connected.False) {
            mNotificationBuilder.addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp,
                    getString(R.string.disconnect), disconnectPendingIntent));
        } else {
            mNotificationBuilder.addAction(new NotificationCompat.Action(R.drawable.ic_clear_white_24dp,
                    getString(R.string.reconnect), reconnectPendingIntent));
        }

        Intent appIntent = new Intent()
                .setClassName(this, Constants.INTENT_CLASS_MAIN_ACTIVITY)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent appPendingIntent = PendingIntent.getActivity(this, 0,
                appIntent,  FLAG_CANCEL_CURRENT | FLAG_IMMUTABLE);

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
            mNotificationBuilder.setContentTitle(getString(R.string.app_name));
        }
        mNotificationBuilder.setContentText(message);
        mNotificationBuilder.setSmallIcon(R.drawable.ic_notification);
        mNotificationBuilder.setColor(getResources().getColor(R.color.colorPrimary));
        mNotificationBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        mNotificationBuilder.setCategory(NotificationCompat.CATEGORY_CALL);
        mNotificationBuilder.setShowWhen(false);
        mNotificationBuilder.setOngoing(true);
        mNotificationBuilder.setContentIntent(appPendingIntent);

        Notification notification = mNotificationBuilder.build();


        if (newNotif) {
            startForeground(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
        } else {
            manager.notify(Constants.NOTIFY_MANAGER_START_FOREGROUND_SERVICE, notification);
        }
    }

    private void cancelNotification() {
        stopForeground(true);
    }

    private void status(int resId) {
        status(getString(resId));
    }

    private void status(String status) {
        Log.d(TAG, status);

        if (mStatusListener != null) {
            mStatusListener.onStatusMessageUpdate(status);
        }
    }
}
