package com.openmobl.pttDriver.bt

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.*
import android.os.*
import android.util.Log
import com.openmobl.pttDriver.Constants
import com.openmobl.pttDriver.bt.hfp.AtCommandHandler
import com.openmobl.pttDriver.bt.hfp.AtCommandResult
import com.openmobl.pttDriver.bt.hfp.AtParser
import com.openmobl.pttDriver.utils.BluetoothScoAudioUtils
import java.io.IOException
import java.security.InvalidParameterException
import java.util.*
import java.util.concurrent.Executors

class HfpSerialSocket(context: Context, device: BluetoothDevice?) : Runnable, SerialSocket {
    private val mDisconnectBroadcastReceiver: BroadcastReceiver
    private val mReconnectBroadcastReceiver: BroadcastReceiver
    private val mContext: Context
    private var mListener: SerialListener? = null
    private val mDevice: BluetoothDevice?
    private var mSocket: BluetoothSocket? = null
    private var mConnected = false
    private var mAtParser: AtParser? = null

    // Some code from com/android/phone/BluetoothHandsFree.java @ jb-release
    private var mRemoteBrsf = 0
    private var mLocalBrsf = 0
    private val mClip = false // Calling Line Information Presentation
    private var mIndicatorsEnabled = false
    private var mCmee = false // Extended Error reporting

    /* Constants for extended AT error codes specified by the Handsfree profile. */
    private object BluetoothCmeError {
        const val AG_FAILURE = 0
        const val NO_CONNECTION_TO_PHONE = 1
        const val OPERATION_NOT_ALLOWED = 3
        const val OPERATION_NOT_SUPPORTED = 4
        const val PIN_REQUIRED = 5
        const val SIM_MISSING = 10
        const val SIM_PIN_REQUIRED = 11
        const val SIM_PUK_REQUIRED = 12
        const val SIM_FAILURE = 13
        const val SIM_BUSY = 14
        const val WRONG_PASSWORD = 16
        const val SIM_PIN2_REQUIRED = 17
        const val SIM_PUK2_REQUIRED = 18
        const val MEMORY_FULL = 20
        const val INVALID_INDEX = 21
        const val MEMORY_FAILURE = 23
        const val TEXT_TOO_LONG = 24
        const val TEXT_HAS_INVALID_CHARS = 25
        const val DIAL_STRING_TOO_LONG = 26
        const val DIAL_STRING_HAS_INVALID_CHARS = 27
        const val NO_SERVICE = 30
        const val ONLY_911_ALLOWED = 32
    }

    // -
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
        initializeAtParser()
    }

    private fun initializeAtParser() {
        mLocalBrsf = BRSF_AG_EC_NR or
                BRSF_AG_REJECT_CALL or
                BRSF_AG_ENHANCED_CALL_STATUS or BRSF_AG_ENHANCED_CALL_CONTROL or BRSF_AG_HF_INDICATORS
        mAtParser = AtParser()

        // Parsing code from com/android/phone/BluetoothHandsfree.java @ jb-release
        mAtParser!!.register("+BRSF", object : AtCommandHandler() {
            private fun sendBRSF(): AtCommandResult {
                return AtCommandResult("+BRSF: $mLocalBrsf")
            }

            override fun handleSetCommand(args: Array<Any>): AtCommandResult {
                // AT+BRSF=<handsfree supported features bitmap>
                // Handsfree is telling us which features it supports. We
                // send the features we support
                if (args.size == 1 && args[0] is Int) {
                    mRemoteBrsf = args[0] as Int
                } else {
                    Log.w(TAG, "HF didn't send BRSF assuming 0")
                }
                return sendBRSF()
            }

            override fun handleActionCommand(): AtCommandResult {
                // This seems to be out of spec, but lets do the nice thing
                return sendBRSF()
            }

            override fun handleReadCommand(): AtCommandResult {
                // This seems to be out of spec, but lets do the nice thing
                return sendBRSF()
            }
        })
        // Mobile Equipment Event Reporting enable/disable command
        // Of the full 3GPP syntax paramters (mode, keyp, disp, ind, bfr) we
        // only support paramter ind (disable/enable evert reporting using
        // +CDEV)
        mAtParser!!.register("+CMER", object : AtCommandHandler() {
            override fun handleReadCommand(): AtCommandResult {
                return AtCommandResult(
                    "+CMER: 3,0,0," + if (mIndicatorsEnabled) "1" else "0"
                )
            }

            override fun handleSetCommand(args: Array<Any>): AtCommandResult {
                if (args.size < 4) {
                    Log.d(TAG, "Args length was " + args.size)
                    for (o in args) {
                        if (o is String) {
                            Log.v(TAG, "Arg String was $o")
                        } else if (o is Int) {
                            Log.v(TAG, "Arg Integer was $o")
                        } else {
                            Log.v(TAG, "Arg Unknown was $o")
                        }
                    }
                    // This is a syntax error
                    return AtCommandResult(AtCommandResult.Companion.ERROR)
                } else if (args[0] == 3 && args[1] == 0 && args[2] == 0) {
                    var valid = false
                    if (args[3] == 0) {
                        mIndicatorsEnabled = false
                        valid = true
                    } else if (args[3] == 1) {
                        mIndicatorsEnabled = true
                        valid = true
                    }
                    /*if (valid) {
                        if ((mRemoteBrsf & BRSF_HF_CW_THREE_WAY_CALLING) == 0x0) {
                            mServiceConnectionEstablished = true;
                            sendURC("OK");  // send immediately, then initiate audio
                            if (isIncallAudio()) {
                                audioOn();
                            } else if (mCM.getFirstActiveRingingCall().isRinging()) {
                                // need to update HS with RING cmd when single
                                // ringing call exist
                                mBluetoothPhoneState.ring();
                            }
                            // only send OK once
                            return new AtCommandResult(AtCommandResult.UNSOLICITED);
                        } else {
                            return new AtCommandResult(AtCommandResult.OK);
                        }
                    }*/return AtCommandResult(AtCommandResult.Companion.OK)
                }
                return reportCmeError(BluetoothCmeError.OPERATION_NOT_SUPPORTED)
            }

            override fun handleTestCommand(): AtCommandResult {
                return AtCommandResult("+CMER: (3),(0),(0),(0-1)")
            }
        })
        // Mobile Equipment Error Reporting enable/disable
        mAtParser!!.register("+CMEE", object : AtCommandHandler() {
            override fun handleActionCommand(): AtCommandResult {
                // out of spec, assume they want to enable
                mCmee = true
                return AtCommandResult(AtCommandResult.Companion.OK)
            }

            override fun handleReadCommand(): AtCommandResult {
                return AtCommandResult("+CMEE: " + if (mCmee) "1" else "0")
            }

            override fun handleSetCommand(args: Array<Any>): AtCommandResult {
                // AT+CMEE=<n>
                return if (args.size == 0) {
                    // <n> ommitted - default to 0
                    mCmee = false
                    AtCommandResult(AtCommandResult.Companion.OK)
                } else if (args[0] !is Int) {
                    // Syntax error
                    AtCommandResult(AtCommandResult.Companion.ERROR)
                } else {
                    mCmee = args[0] as Int == 1
                    AtCommandResult(AtCommandResult.Companion.OK)
                }
            }

            override fun handleTestCommand(): AtCommandResult {
                // Probably not required but spec, but no harm done
                return AtCommandResult("+CMEE: (0-1)")
            }
        })
        // Indicator Update command
        mAtParser!!.register("+CIND", object : AtCommandHandler() {
            override fun handleReadCommand(): AtCommandResult {
                val result = AtCommandResult(AtCommandResult.Companion.OK)
                val status = "+CIND: " + "1" + "," + "1" + "," + "0" + "," +
                        "0" + "," + "5" + "," + "0" + "," + "5" + "," + "1" + "," + "0" // TODO
                result.addResponse(status)
                return result
            }

            override fun handleTestCommand(): AtCommandResult {
                return AtCommandResult(
                    "+CIND: (\"service\",(0-1))," + "(\"call\",(0-1))," +
                            "(\"callsetup\",(0-3)),(\"callheld\",(0-2)),(\"signal\",(0-5))," +
                            "(\"roam\",(0-1)),(\"battchg\",(0-5)),(\"message\",(0-1)),(\"smsfull\",(0-2))"
                )
            }
        })
        // Query Signal Quality (legacy)
        mAtParser!!.register("+CSQ", object : AtCommandHandler() {
            override fun handleActionCommand(): AtCommandResult {
                val result = AtCommandResult(AtCommandResult.Companion.OK)
                val status = "+CSQ: 31,99" // TODO
                result.addResponse(status)
                return result
            }
        })
        // Query network registration state
        mAtParser!!.register("+CREG", object : AtCommandHandler() {
            override fun handleReadCommand(): AtCommandResult {
                return AtCommandResult("+CREG: 1,1") // TODO
            }
        })
        // Bluetooth Response and Hold
        mAtParser!!.register("+BTRH", object : AtCommandHandler() {
            override fun handleReadCommand(): AtCommandResult {
                //return new AtCommandResult("+BTRH: 0");

                //AtCommandResult result = new AtCommandResult(AtCommandResult.OK);
                val result = AtCommandResult(AtCommandResult.Companion.UNSOLICITED) //OK);
                //result.addResponse("+BTRH: 0");
                result.addResponse("OK")
                result.addResponse("+PTT=?")
                /*result.addResponse("+CIEV: 1,1");
                result.addResponse("+CIEV: 2,1");
                int toa = 0x81;
                result.addResponse("+CLCC: 1,0,0,0,0,\"" + "100" + "\"," + toa);*/try {
                    val handler = Handler(mContext.mainLooper)
                    handler.postDelayed({
                        BluetoothScoAudioUtils.giveDevicePriority(
                            mContext,
                            mDevice
                        )
                    }, 1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return result
            }

            override fun handleSetCommand(args: Array<Any>): AtCommandResult {
                return AtCommandResult(AtCommandResult.Companion.OK)
            }
        })
        // Microphone Gain
        mAtParser!!.register("+VGM", object : AtCommandHandler() {
            override fun handleSetCommand(args: Array<Any>): AtCommandResult {
                // AT+VGM=<gain>    in range [0,15]
                // Headset/Handsfree is reporting its current gain setting
                return AtCommandResult(AtCommandResult.Companion.OK)
            }
        })
        // Speaker Gain
        mAtParser!!.register("+VGS", object : AtCommandHandler() {
            override fun handleSetCommand(args: Array<Any>): AtCommandResult {
                // AT+VGS=<gain>    in range [0,15]
                return if (args.size != 1 || args[0] !is Int) {
                    AtCommandResult(AtCommandResult.Companion.ERROR)
                } else AtCommandResult(AtCommandResult.Companion.OK)
                // TODO: Implement
                //mScoGain = (Integer) args[0];
                //int flag =  mAudioManager.isBluetoothScoOn() ? AudioManager.FLAG_SHOW_UI:0;
                //mAudioManager.setStreamVolume(AudioManager.STREAM_BLUETOOTH_SCO, mScoGain, flag);
            }
        })

        // Catch-all handler
        mAtParser!!.register("", object : AtCommandHandler() {
            private fun bubbleUp(command: String) {}
            override fun handleBasicCommand(arg: String?): AtCommandResult {
                return AtCommandResult(AtCommandResult.Companion.OK)
            }

            override fun handleSetCommand(args: Array<Any>): AtCommandResult {
                return AtCommandResult(AtCommandResult.Companion.OK)
            }

            override fun handleActionCommand(): AtCommandResult {
                return AtCommandResult(AtCommandResult.Companion.OK)
            }

            override fun handleReadCommand(): AtCommandResult {
                return AtCommandResult(AtCommandResult.Companion.OK)
            }

            override fun handleTestCommand(): AtCommandResult {
                return AtCommandResult(AtCommandResult.Companion.OK)
            }
        })
    }

    private fun reportCmeError(error: Int): AtCommandResult {
        return if (mCmee) {
            val result = AtCommandResult(AtCommandResult.Companion.UNSOLICITED)
            result.addResponse("+CME ERROR: $error")
            result
        } else {
            AtCommandResult(AtCommandResult.Companion.ERROR)
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
        if (DEBUG) Log.v(TAG, "Writing data: " + String(data) + " - data = " + toHexString(data))
        if (!mConnected) throw IOException("not connected")
        mSocket!!.outputStream.write(data)
    }

    fun processAtCommands(data: String) {
        var data = data
        if (DEBUG) {
            val dataBytes = data.toByteArray()
            Log.v(
                TAG,
                "processAtCommands - data = " + toHexString(dataBytes) + ", dataStr = " + data
            )
        }
        data = data.replace("\r", "")
        data = data.replace("\n", "")
        val result = mAtParser!!.process(data)
        try {
            write(result.toString().toByteArray())
        } catch (e: Exception) {
            Log.d(TAG, "Exception in processAtCommands: $e")
            e.printStackTrace()
        }
    }

    private fun onSerialRead(data: ByteArray) {
        if (mListener != null) mListener!!.onSerialRead(data, null, null)
    }

    override fun run() { // connect & read
        var sendIoErrorException: Exception? = null
        try {
            mSocket = mDevice!!.createRfcommSocketToServiceRecord(BLUETOOTH_HFP)
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
                onSerialRead(data)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Exception in HFP run loop: $e")
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
        private val TAG = HfpSerialSocket::class.java.name
        private val BLUETOOTH_HFP = UUID.fromString("0000111e-0000-1000-8000-00805F9B34FB")
        private const val DEBUG = true

        /* Constants from Bluetooth Specification Hands-Free profile version 1.5 */
        private const val BRSF_AG_THREE_WAY_CALLING = 1 shl 0
        private const val BRSF_AG_EC_NR = 1 shl 1
        private const val BRSF_AG_VOICE_RECOG = 1 shl 2
        private const val BRSF_AG_IN_BAND_RING = 1 shl 3
        private const val BRSF_AG_VOICE_TAG_NUMBE = 1 shl 4
        private const val BRSF_AG_REJECT_CALL = 1 shl 5
        private const val BRSF_AG_ENHANCED_CALL_STATUS = 1 shl 6
        private const val BRSF_AG_ENHANCED_CALL_CONTROL = 1 shl 7
        private const val BRSF_AG_ENHANCED_ERR_RESULT_CODES = 1 shl 8
        private const val BRSF_AG_CODEC_NEG = 1 shl 9
        private const val BRSF_AG_HF_INDICATORS = 1 shl 10
        private const val BRSF_HF_EC_NR = 1 shl 0
        private const val BRSF_HF_CW_THREE_WAY_CALLING = 1 shl 1
        private const val BRSF_HF_CLIP = 1 shl 2
        private const val BRSF_HF_VOICE_REG_ACT = 1 shl 3
        private const val BRSF_HF_REMOTE_VOL_CONTROL = 1 shl 4
        private const val BRSF_HF_ENHANCED_CALL_STATUS = 1 shl 5
        private const val BRSF_HF_ENHANCED_CALL_CONTROL = 1 shl 6
        private const val BRSF_HF_CODEC_NEG = 1 shl 7
        private const val BRSF_HF_INDICATORS = 1 shl 8
    }
}