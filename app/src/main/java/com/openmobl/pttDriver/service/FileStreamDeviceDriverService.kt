package com.openmobl.pttDriver.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import bsh.EvalError
import bsh.Interpreter
import bsh.ParseException
import bsh.TargetError
import com.openmobl.pttDriver.BuildConfig
import com.openmobl.pttDriver.Constants
import com.openmobl.pttDriver.R
import com.openmobl.pttDriver.model.Device
import com.openmobl.pttDriver.model.PttDriver
import com.openmobl.pttDriver.model.PttDriver.IntentMap
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.FileInputStream

class FileStreamDeviceDriverService : Service(), IDeviceDriverService {
    override var connectionState = DeviceConnectionState.Disconnected
        private set
    private var mDeviceDefined = false
    private var mPttDriver: PttDriver? = null
    private var mPttDownKeyDelay = 0
    private var mPttDownKeyDelayOverride = false

    /*private String mLastIntentSent;
    private Date mLastIntentSentTime;*/
    private var mEventConductor: Handler? = null
    private var mFileWatchers: MutableMap<String?, EventFileWatcher>? = null
    private var mNotificationBuilder: NotificationCompat.Builder? = null
    private var mStatusListener: DeviceStatusListener? = null

    abstract class EventListener {
        abstract fun onEvent(file: EventFileWatcher)
    }

    class EventFileWatcher(
        fileObj: PttDriver.FileObject?,
        listener: EventListener?,
        eventConductor: Handler?
    ) : FileObserver(fileObj.getFileName(), ACCESS) {
        val filename: String?
        val preprocessFunctionName: String?
        val intentMap: IntentMap
        private var mFileStream: FileInputStream? = null
        private var mBufferedStream: BufferedInputStream? = null
        var dataStream: DataInputStream? = null
            private set
        private val mConductor: Handler?
        private val mListener: EventListener?

        init {
            filename = fileObj.getFileName()
            preprocessFunctionName = fileObj.getPreprocessFunction()
            intentMap = fileObj.getIntentMap().clone()
            mListener = listener
            mConductor = eventConductor
        }

        fun createDataStream() {
            if (filename != null && !filename.isEmpty()) {
                try {
                    mFileStream = FileInputStream(filename)
                    mBufferedStream = BufferedInputStream(mFileStream)
                    dataStream = DataInputStream(mBufferedStream)
                } catch (e: Exception) {
                    Log.d(TAG, "Exception " + e + " opening " + filename)
                    e.printStackTrace()
                }
            }
        }

        fun close() {
            try {
                dataStream!!.close()
                dataStream = null
                mBufferedStream!!.close()
                mBufferedStream = null
                mFileStream!!.close()
                mFileStream = null
            } catch (e: Exception) {
                Log.d(TAG, "Exception $e when closing streams")
                e.printStackTrace()
            }
        }

        override fun onEvent(event: Int, path: String?) {
            if (mListener != null && mConductor != null) {
                val that = this
                mConductor.post(Runnable { mListener.onEvent(that) })
            }
        }
    }

    private val mEventResponder: EventListener = object : EventListener() {
        override fun onEvent(fileObj: EventFileWatcher) {
            val result = executePreprocessor(fileObj.preprocessFunctionName, fileObj.dataStream)
            if (result != null && !result.isEmpty()) {
                val intentName = fileObj.intentMap[result]
                if (intentName != null && !intentName.isEmpty()) {
                    var delay = 0
                    if (mPttDriver.getReadObj().pttDownKeyIntent != null && intentName == mPttDriver.getReadObj().pttDownKeyIntent) {
                        // If the user did not override the ptt key down delay, check to see what the driver defines
                        delay =
                            if (mPttDownKeyDelayOverride) mPttDownKeyDelay else mPttDriver.getReadObj().defaultPttDownKeyDelay
                    }
                    sendIntent(intentName, delay)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.v(TAG, "onStartCommand")
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "onCreate")
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        createNotification(getString(R.string.status_disconnected))
        mEventConductor = Handler(mainLooper)
        mFileWatchers = HashMap()
        mDeviceDefined = false
        mPttDownKeyDelay = 0
        mPttDownKeyDelayOverride = false
    }

    override fun onDestroy() {
        Log.v(TAG, "onDestroy")
        if (connectionState != DeviceConnectionState.Disconnected) disconnect()
        cancelNotification()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.v(TAG, "onBind")
        return DeviceDriverServiceBinder(this)
    }

    override fun setPttDevice(device: Device?) {
        Log.v(TAG, "setPttDevice")
        if (device != null) {
            mPttDownKeyDelay = device.pttDownDelay
            mPttDownKeyDelayOverride = true
            mDeviceDefined = true
        }
    }

    override fun deviceIsValid(): Boolean {
        return true
    }

    override fun setPttDriver(driver: PttDriver?) {
        Log.v(TAG, "setPttDriver")
        mPttDriver = driver
    }

    override var automaticallyReconnect: Boolean
        get() = true
        set(autoReconnect) {}

    override fun registerStatusListener(statusListener: DeviceStatusListener?) {
        mStatusListener = statusListener
    }

    override fun unregisterStatusListener(statusListener: DeviceStatusListener?) {
        mStatusListener = null
    }

    override fun connect() {
        Log.v(TAG, "connect")
        if (connectionState == DeviceConnectionState.Disconnected) {
            if (mPttDriver != null && mDeviceDefined) {
                for (fileObj in mPttDriver.getReadObj().files) {
                    val watcher = EventFileWatcher(fileObj, mEventResponder, mEventConductor)
                    mFileWatchers!![fileObj.fileName] = watcher
                    watcher.createDataStream()
                }
                connectionState = DeviceConnectionState.Connected
                if (mStatusListener != null) {
                    mStatusListener!!.onConnected()
                }
            } else if (mStatusListener != null) {
                mStatusListener!!.onStatusMessageUpdate("Device or driver not set") // TODO Use a string resource
            }
        }
    }

    override fun disconnect() {
        Log.v(TAG, "disconnect")
        if (connectionState == DeviceConnectionState.Connected) {
            connectionState = DeviceConnectionState.Disconnected
            for ((_, watcher) in mFileWatchers!!) {
                watcher.close()
            }
            if (mStatusListener != null) {
                mStatusListener!!.onDisconnected()
            }
        }
    }

    /*private void initializeIfReady() {

    }*/
    private fun executePreprocessor(function: String?, data: DataInputStream?): String? {
        var result = ""
        val interpreter = Interpreter()
        try {
            val code = mPttDriver.getReadObj().operationsMap[function]
            if (code != null && !code.isEmpty()) {
                interpreter["data"] = data
                val resultObj = interpreter.eval(code)
                if (resultObj is String) {
                    result = resultObj
                }
            }
        } catch (e: TargetError) {
            val t = e.target
            Log.d(TAG, "Exception thrown: $e, $t")
            e.printStackTrace()
        } catch (e: ParseException) {
            Log.d(TAG, "Parse Exception thrown: " + e + ", " + e.errorText)
            e.printStackTrace()
        } catch (e: EvalError) {
            Log.d(TAG, "Eval Exception thrown: " + e + ", " + e.errorText)
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun sendIntentInternal(intentName: String) {
        // Possibility that user pressed the key down and released it before the key down delay.
        // There might be an edge case here, so we *might* want to also include a timestamp with the
        // call to sendIntentInternal.
        /*if (!intentName.equals(mLastIntentSent)) {
            return;
        }*/
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

    private fun sendIntent(intentName: String, delay: Int) {
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
            val handler = Handler(mainLooper)
            handler.postDelayed({ sendIntentInternal(intentName) }, delay.toLong())
        } else {
            sendIntentInternal(intentName)
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
                    BuildConfig.APPLICATION_ID + "." + FileStreamDeviceDriverService::class.java.name
                val channelName = getString(R.string.filestream_background_service)
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

    companion object {
        private val TAG = FileStreamDeviceDriverService::class.java.name
    }
}