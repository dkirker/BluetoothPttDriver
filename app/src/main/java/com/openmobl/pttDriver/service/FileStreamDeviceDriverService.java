package com.openmobl.pttDriver.service;

import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.ParseException;
import bsh.TargetError;

import com.openmobl.pttDriver.BuildConfig;
import com.openmobl.pttDriver.Constants;
import com.openmobl.pttDriver.R;
import com.openmobl.pttDriver.model.Device;
import com.openmobl.pttDriver.model.PttDriver;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;


public class FileStreamDeviceDriverService extends Service implements IDeviceDriverService {
    private static final String TAG = FileStreamDeviceDriverService.class.getName();

    private DeviceConnectionState mConnectionState = DeviceConnectionState.Disconnected;

    private boolean mDeviceDefined;
    private PttDriver mPttDriver;
    private int mPttDownKeyDelay;
    private boolean mPttDownKeyDelayOverride;
    /*private String mLastIntentSent;
    private Date mLastIntentSentTime;*/

    private Handler mEventConductor;

    private Map<String, EventFileWatcher> mFileWatchers;

    private NotificationCompat.Builder mNotificationBuilder;

    private DeviceStatusListener mStatusListener;

    public static abstract class EventListener {
        public abstract void onEvent(final EventFileWatcher file);
    }

    public static class EventFileWatcher extends FileObserver {
        private final String mFilename;
        private final String mPreprocessFunction;
        private final PttDriver.IntentMap mIntentMap;
        private FileInputStream mFileStream;
        private BufferedInputStream mBufferedStream;
        private DataInputStream mDataStream;
        private Handler mConductor;

        private final EventListener mListener;

        public EventFileWatcher(PttDriver.FileObject fileObj, EventListener listener, Handler eventConductor) {
            super(fileObj.getFileName(), ACCESS);

            mFilename = fileObj.getFileName();
            mPreprocessFunction = fileObj.getPreprocessFunction();
            mIntentMap = (PttDriver.IntentMap)fileObj.getIntentMap().clone();
            mListener = listener;
            mConductor = eventConductor;
        }

        public void createDataStream() {
            if (mFilename != null && !mFilename.isEmpty()) {
                try {
                    mFileStream = new FileInputStream(mFilename);
                    mBufferedStream = new BufferedInputStream(mFileStream);
                    mDataStream = new DataInputStream(mBufferedStream);
                } catch (Exception e) {
                    Log.d(TAG, "Exception " + e + " opening " + mFilename);
                    e.printStackTrace();
                }
            }
        }

        public DataInputStream getDataStream() {
            return mDataStream;
        }

        public String getFilename() {
            return mFilename;
        }

        public String getPreprocessFunctionName() {
            return mPreprocessFunction;
        }

        public PttDriver.IntentMap getIntentMap() {
            return mIntentMap;
        }

        public void close() {
            try {
                mDataStream.close();
                mDataStream = null;

                mBufferedStream.close();
                mBufferedStream = null;

                mFileStream.close();
                mFileStream = null;
            } catch (Exception e) {
                Log.d(TAG, "Exception " + e + " when closing streams");
                e.printStackTrace();
            }
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            if (mListener != null && mConductor != null) {
                EventFileWatcher that = this;

                mConductor.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onEvent(that);
                    }
                });
            }
        }
    }

    private final EventListener mEventResponder = new EventListener() {
            @Override
            public void onEvent(final EventFileWatcher fileObj) {
                String result = executePreprocessor(fileObj.getPreprocessFunctionName(), fileObj.getDataStream());

                if (result != null && !result.isEmpty()) {
                    String intentName = fileObj.getIntentMap().get(result);

                    if (intentName != null && !intentName.isEmpty()) {
                        int delay = 0;

                        if (mPttDriver.getReadObj().getPttDownKeyIntent() != null &&
                                intentName.equals(mPttDriver.getReadObj().getPttDownKeyIntent())) {
                            // If the user did not override the ptt key down delay, check to see what the driver defines
                            delay = mPttDownKeyDelayOverride ? mPttDownKeyDelay : mPttDriver.getReadObj().getDefaultPttDownKeyDelay();
                        }

                        sendIntent(intentName, delay);
                    }
                }
            }
        };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        createNotification(getString(R.string.status_disconnected));

        mEventConductor = new Handler(getMainLooper());
        mFileWatchers = new HashMap<>();

        mDeviceDefined = false;
        mPttDownKeyDelay = 0;
        mPttDownKeyDelayOverride = false;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");
        if (mConnectionState != DeviceConnectionState.Disconnected)
            disconnect();

        cancelNotification();

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "onBind");
        return new DeviceDriverServiceBinder(this);
    }

    @Override
    public void setPttDevice(Device device) {
        Log.v(TAG, "setPttDevice");
        if (device != null) {
            mPttDownKeyDelay = device.getPttDownDelay();
            mPttDownKeyDelayOverride = true;

            mDeviceDefined = true;
        }
    }

    @Override
    public boolean deviceIsValid() {
        return true;
    }

    @Override
    public void setPttDriver(PttDriver driver) {
        Log.v(TAG, "setPttDriver");

        mPttDriver = driver;
    }

    @Override
    public void setAutomaticallyReconnect(boolean autoReconnect) {

    }

    @Override
    public boolean getAutomaticallyReconnect() {
        return true;
    }

    @Override
    public void registerStatusListener(DeviceStatusListener statusListener) {
        mStatusListener = statusListener;
    }
    @Override
    public void unregisterStatusListener(DeviceStatusListener statusListener) {
        mStatusListener = null;
    }

    @Override
    public DeviceConnectionState getConnectionState() {
        return mConnectionState;
    }

    @Override
    public void connect() {
        Log.v(TAG, "connect");

        if (mConnectionState == DeviceConnectionState.Disconnected) {
            if (mPttDriver != null && mDeviceDefined) {
                for (PttDriver.FileObject fileObj: mPttDriver.getReadObj().getFiles()) {
                    EventFileWatcher watcher = new EventFileWatcher(fileObj, mEventResponder, mEventConductor);

                    mFileWatchers.put(fileObj.getFileName(), watcher);

                    watcher.createDataStream();
                }

                mConnectionState = DeviceConnectionState.Connected;

                if (mStatusListener != null) {
                    mStatusListener.onConnected();
                }
            } else if (mStatusListener != null) {
                mStatusListener.onStatusMessageUpdate("Device or driver not set"); // TODO Use a string resource
            }
        }
    }

    @Override
    public void disconnect() {
        Log.v(TAG, "disconnect");

        if (mConnectionState == DeviceConnectionState.Connected) {
            mConnectionState = DeviceConnectionState.Disconnected;

            for (Map.Entry<String, EventFileWatcher> fileWatcher: mFileWatchers.entrySet()) {
                EventFileWatcher watcher = fileWatcher.getValue();

                watcher.close();
            }

            if (mStatusListener != null) {
                mStatusListener.onDisconnected();
            }
        }
    }

    /*private void initializeIfReady() {

    }*/

    private String executePreprocessor(String function, DataInputStream data) {
        String result = "";
        Interpreter interpreter = new Interpreter();

        try {
            String code = mPttDriver.getReadObj().getOperationsMap().get(function);

            if (code != null && !code.isEmpty()) {
                interpreter.set("data", data);

                Object resultObj = interpreter.eval(code);

                if (resultObj instanceof String) {
                    result = (String) resultObj;
                }
            }
        } catch (TargetError e) {
            Throwable t = e.getTarget();
            Log.d(TAG, "Exception thrown: " + e + ", " + t);
            e.printStackTrace();
        } catch (ParseException e) {
            Log.d(TAG, "Parse Exception thrown: " + e + ", " + e.getErrorText());
            e.printStackTrace();
        } catch (EvalError e) {
            Log.d(TAG, "Eval Exception thrown: " + e + ", " + e.getErrorText());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private void sendIntentInternal(final String intentName) {
        // Possibility that user pressed the key down and released it before the key down delay.
        // There might be an edge case here, so we *might* want to also include a timestamp with the
        // call to sendIntentInternal.
        /*if (!intentName.equals(mLastIntentSent)) {
            return;
        }*/
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

    private void sendIntent(final String intentName, int delay) {
        /*Date now = new Date();

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
        mLastIntentSentTime = new Date();*/

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

    private void createNotification(String message) {
        String channelId = "";
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        boolean newNotif = false;

        if (mNotificationBuilder == null) {
            newNotif = true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                channelId = BuildConfig.APPLICATION_ID + "." + FileStreamDeviceDriverService.class.getName();
                String channelName = getString(R.string.filestream_background_service);
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
        if (mConnectionState != DeviceConnectionState.Disconnected) {
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
}