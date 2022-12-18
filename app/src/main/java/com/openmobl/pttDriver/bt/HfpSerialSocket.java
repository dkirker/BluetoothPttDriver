package com.openmobl.pttDriver.bt;

import com.openmobl.pttDriver.Constants;
import com.openmobl.pttDriver.R;
import com.openmobl.pttDriver.bt.hfp.AtCommandHandler;
import com.openmobl.pttDriver.bt.hfp.AtCommandResult;
import com.openmobl.pttDriver.bt.hfp.AtParser;
import com.openmobl.pttDriver.utils.BluetoothScoAudioUtils;
import com.openmobl.pttDriver.utils.TextUtil;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

public class HfpSerialSocket implements Runnable, SerialSocket {
    private static final String TAG = HfpSerialSocket.class.getName();

    private static final UUID BLUETOOTH_HFP = UUID.fromString("0000111e-0000-1000-8000-00805F9B34FB");

    private static final boolean DEBUG = true;

    private final BroadcastReceiver mDisconnectBroadcastReceiver;
    private final BroadcastReceiver mReconnectBroadcastReceiver;

    private final Context mContext;
    private SerialListener mListener;
    private final BluetoothDevice mDevice;
    private BluetoothSocket mSocket;
    private boolean mConnected;
    private AtParser mAtParser;

    // Some code from com/android/phone/BluetoothHandsFree.java @ jb-release
    private int mRemoteBrsf = 0;
    private int mLocalBrsf = 0;
    /* Constants from Bluetooth Specification Hands-Free profile version 1.5 */
    private static final int BRSF_AG_THREE_WAY_CALLING = 1 << 0;
    private static final int BRSF_AG_EC_NR = 1 << 1;
    private static final int BRSF_AG_VOICE_RECOG = 1 << 2;
    private static final int BRSF_AG_IN_BAND_RING = 1 << 3;
    private static final int BRSF_AG_VOICE_TAG_NUMBE = 1 << 4;
    private static final int BRSF_AG_REJECT_CALL = 1 << 5;
    private static final int BRSF_AG_ENHANCED_CALL_STATUS = 1 <<  6;
    private static final int BRSF_AG_ENHANCED_CALL_CONTROL = 1 << 7;
    private static final int BRSF_AG_ENHANCED_ERR_RESULT_CODES = 1 << 8;
    private static final int BRSF_AG_CODEC_NEG = 1 << 9;
    private static final int BRSF_AG_HF_INDICATORS = 1 << 10;

    private static final int BRSF_HF_EC_NR = 1 << 0;
    private static final int BRSF_HF_CW_THREE_WAY_CALLING = 1 << 1;
    private static final int BRSF_HF_CLIP = 1 << 2;
    private static final int BRSF_HF_VOICE_REG_ACT = 1 << 3;
    private static final int BRSF_HF_REMOTE_VOL_CONTROL = 1 << 4;
    private static final int BRSF_HF_ENHANCED_CALL_STATUS = 1 <<  5;
    private static final int BRSF_HF_ENHANCED_CALL_CONTROL = 1 << 6;
    private static final int BRSF_HF_CODEC_NEG = 1 << 7;
    private static final int BRSF_HF_INDICATORS = 1 << 8;

    private boolean mClip = false;  // Calling Line Information Presentation
    private boolean mIndicatorsEnabled = false;
    private boolean mCmee = false;  // Extended Error reporting

    /* Constants for extended AT error codes specified by the Handsfree profile. */
    private class BluetoothCmeError {
        public static final int AG_FAILURE = 0;
        public static final int NO_CONNECTION_TO_PHONE = 1;
        public static final int OPERATION_NOT_ALLOWED = 3;
        public static final int OPERATION_NOT_SUPPORTED = 4;
        public static final int PIN_REQUIRED = 5;
        public static final int SIM_MISSING = 10;
        public static final int SIM_PIN_REQUIRED = 11;
        public static final int SIM_PUK_REQUIRED = 12;
        public static final int SIM_FAILURE = 13;
        public static final int SIM_BUSY = 14;
        public static final int WRONG_PASSWORD = 16;
        public static final int SIM_PIN2_REQUIRED = 17;
        public static final int SIM_PUK2_REQUIRED = 18;
        public static final int MEMORY_FULL = 20;
        public static final int INVALID_INDEX = 21;
        public static final int MEMORY_FAILURE = 23;
        public static final int TEXT_TOO_LONG = 24;
        public static final int TEXT_HAS_INVALID_CHARS = 25;
        public static final int DIAL_STRING_TOO_LONG = 26;
        public static final int DIAL_STRING_HAS_INVALID_CHARS = 27;
        public static final int NO_SERVICE = 30;
        public static final int ONLY_911_ALLOWED = 32;
    }

    // -

    public HfpSerialSocket(Context context, BluetoothDevice device) {
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

        initializeAtParser();
    }

    private void initializeAtParser() {
        mLocalBrsf = BRSF_AG_EC_NR |
                BRSF_AG_REJECT_CALL |
                BRSF_AG_ENHANCED_CALL_STATUS |
                BRSF_AG_ENHANCED_CALL_CONTROL |
                BRSF_AG_HF_INDICATORS;

        mAtParser = new AtParser();

        // Parsing code from com/android/phone/BluetoothHandsfree.java @ jb-release
        mAtParser.register("+BRSF", new AtCommandHandler() {
            private AtCommandResult sendBRSF() {
                return new AtCommandResult("+BRSF: " + mLocalBrsf);
            }
            @Override
            public AtCommandResult handleSetCommand(Object[] args) {
                // AT+BRSF=<handsfree supported features bitmap>
                // Handsfree is telling us which features it supports. We
                // send the features we support
                if (args.length == 1 && (args[0] instanceof Integer)) {
                    mRemoteBrsf = (Integer) args[0];
                } else {
                    Log.w(TAG, "HF didn't send BRSF assuming 0");
                }
                return sendBRSF();
            }
            @Override
            public AtCommandResult handleActionCommand() {
                // This seems to be out of spec, but lets do the nice thing
                return sendBRSF();
            }
            @Override
            public AtCommandResult handleReadCommand() {
                // This seems to be out of spec, but lets do the nice thing
                return sendBRSF();
            }
        });
        // Mobile Equipment Event Reporting enable/disable command
        // Of the full 3GPP syntax paramters (mode, keyp, disp, ind, bfr) we
        // only support paramter ind (disable/enable evert reporting using
        // +CDEV)
        mAtParser.register("+CMER", new AtCommandHandler() {
            @Override
            public AtCommandResult handleReadCommand() {
                return new AtCommandResult(
                        "+CMER: 3,0,0," + (mIndicatorsEnabled ? "1" : "0"));
            }
            @Override
            public AtCommandResult handleSetCommand(Object[] args) {
                if (args.length < 4) {
                    Log.d(TAG, "Args length was " + args.length);
                    for (Object o : args) {
                        if (o instanceof String) {
                            String arg = (String)o;
                            Log.v(TAG, "Arg String was " + arg);
                        } else if (o instanceof Integer) {
                            Integer arg = (Integer)o;
                            Log.v(TAG, "Arg Integer was " + arg);
                        } else {
                            Log.v(TAG, "Arg Unknown was " + o);
                        }
                    }
                    // This is a syntax error
                    return new AtCommandResult(AtCommandResult.ERROR);
                } else if (args[0].equals(3) && args[1].equals(0) &&
                        args[2].equals(0)) {
                    boolean valid = false;
                    if (args[3].equals(0)) {
                        mIndicatorsEnabled = false;
                        valid = true;
                    } else if (args[3].equals(1)) {
                        mIndicatorsEnabled = true;
                        valid = true;
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
                    }*/
                    return new AtCommandResult(AtCommandResult.OK);
                }
                return reportCmeError(BluetoothCmeError.OPERATION_NOT_SUPPORTED);
            }
            @Override
            public AtCommandResult handleTestCommand() {
                return new AtCommandResult("+CMER: (3),(0),(0),(0-1)");
            }
        });
        // Mobile Equipment Error Reporting enable/disable
        mAtParser.register("+CMEE", new AtCommandHandler() {
            @Override
            public AtCommandResult handleActionCommand() {
                // out of spec, assume they want to enable
                mCmee = true;
                return new AtCommandResult(AtCommandResult.OK);
            }
            @Override
            public AtCommandResult handleReadCommand() {
                return new AtCommandResult("+CMEE: " + (mCmee ? "1" : "0"));
            }
            @Override
            public AtCommandResult handleSetCommand(Object[] args) {
                // AT+CMEE=<n>
                if (args.length == 0) {
                    // <n> ommitted - default to 0
                    mCmee = false;
                    return new AtCommandResult(AtCommandResult.OK);
                } else if (!(args[0] instanceof Integer)) {
                    // Syntax error
                    return new AtCommandResult(AtCommandResult.ERROR);
                } else {
                    mCmee = ((Integer)args[0] == 1);
                    return new AtCommandResult(AtCommandResult.OK);
                }
            }
            @Override
            public AtCommandResult handleTestCommand() {
                // Probably not required but spec, but no harm done
                return new AtCommandResult("+CMEE: (0-1)");
            }
        });
        // Indicator Update command
        mAtParser.register("+CIND", new AtCommandHandler() {
            @Override
            public AtCommandResult handleReadCommand() {
                AtCommandResult result = new AtCommandResult(AtCommandResult.OK);
                String status = "+CIND: " + "1" + "," + "1" + "," + "0" + "," +
                        "0" + "," + "5" + "," + "0" + "," + "5" + "," + "1" + "," + "0"; // TODO
                result.addResponse(status);
                return result;
            }
            @Override
            public AtCommandResult handleTestCommand() {
                return new AtCommandResult("+CIND: (\"service\",(0-1))," + "(\"call\",(0-1))," +
                        "(\"callsetup\",(0-3)),(\"callheld\",(0-2)),(\"signal\",(0-5))," +
                        "(\"roam\",(0-1)),(\"battchg\",(0-5)),(\"message\",(0-1)),(\"smsfull\",(0-2))");
            }
        });
        // Query Signal Quality (legacy)
        mAtParser.register("+CSQ", new AtCommandHandler() {
            @Override
            public AtCommandResult handleActionCommand() {
                AtCommandResult result = new AtCommandResult(AtCommandResult.OK);
                String status = "+CSQ: 31,99"; // TODO
                result.addResponse(status);
                return result;
            }
        });
        // Query network registration state
        mAtParser.register("+CREG", new AtCommandHandler() {
            @Override
            public AtCommandResult handleReadCommand() {
                return new AtCommandResult("+CREG: 1,1"); // TODO
            }
        });
        // Bluetooth Response and Hold
        mAtParser.register("+BTRH", new AtCommandHandler() {
            @Override
            public AtCommandResult handleReadCommand() {
                //return new AtCommandResult("+BTRH: 0");

                //AtCommandResult result = new AtCommandResult(AtCommandResult.OK);
                AtCommandResult result = new AtCommandResult(AtCommandResult.UNSOLICITED); //OK);
                //result.addResponse("+BTRH: 0");
                result.addResponse("OK");
                result.addResponse("+PTT=?");
                /*result.addResponse("+CIEV: 1,1");
                result.addResponse("+CIEV: 2,1");
                int toa = 0x81;
                result.addResponse("+CLCC: 1,0,0,0,0,\"" + "100" + "\"," + toa);*/



                try {
                    final Handler handler = new Handler(mContext.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            BluetoothScoAudioUtils.giveDevicePriority(mContext, mDevice);
                        }
                    }, 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return result;
            }
            @Override
            public AtCommandResult handleSetCommand(Object[] args) {
                return new AtCommandResult(AtCommandResult.OK);
            }
        });
        // Microphone Gain
        mAtParser.register("+VGM", new AtCommandHandler() {
            @Override
            public AtCommandResult handleSetCommand(Object[] args) {
                // AT+VGM=<gain>    in range [0,15]
                // Headset/Handsfree is reporting its current gain setting
                return new AtCommandResult(AtCommandResult.OK);
            }
        });
        // Speaker Gain
        mAtParser.register("+VGS", new AtCommandHandler() {
            @Override
            public AtCommandResult handleSetCommand(Object[] args) {
                // AT+VGS=<gain>    in range [0,15]
                if (args.length != 1 || !(args[0] instanceof Integer)) {
                    return new AtCommandResult(AtCommandResult.ERROR);
                }
                // TODO: Implement
                //mScoGain = (Integer) args[0];
                //int flag =  mAudioManager.isBluetoothScoOn() ? AudioManager.FLAG_SHOW_UI:0;
                //mAudioManager.setStreamVolume(AudioManager.STREAM_BLUETOOTH_SCO, mScoGain, flag);
                return new AtCommandResult(AtCommandResult.OK);
            }
        });

        // Catch-all handler
        mAtParser.register("", new AtCommandHandler() {
            private void bubbleUp(String command) {

            }
            @Override
            public AtCommandResult handleBasicCommand(String arg) {
                return new AtCommandResult(AtCommandResult.OK);
            }
            @Override
            public AtCommandResult handleSetCommand(Object[] args) {
                return new AtCommandResult(AtCommandResult.OK);
            }
            @Override
            public AtCommandResult handleActionCommand() {
                return new AtCommandResult(AtCommandResult.OK);
            }
            @Override
            public AtCommandResult handleReadCommand() {
                return new AtCommandResult(AtCommandResult.OK);
            }
            @Override
            public AtCommandResult handleTestCommand() {
                return new AtCommandResult(AtCommandResult.OK);
            }
        });
    }
    private AtCommandResult reportCmeError(int error) {
        if (mCmee) {
            AtCommandResult result = new AtCommandResult(AtCommandResult.UNSOLICITED);
            result.addResponse("+CME ERROR: " + error);
            return result;
        } else {
            return new AtCommandResult(AtCommandResult.ERROR);
        }
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
        if (DEBUG)
            Log.v(TAG, "Writing data: " + new String(data) + " - data = " + TextUtil.toHexString(data));

        if (!mConnected)
            throw new IOException("not connected");

        mSocket.getOutputStream().write(data);
    }

    public void processAtCommands(String data) {
        if (DEBUG) {
            byte[] dataBytes = data.getBytes();
            Log.v(TAG, "processAtCommands - data = " + TextUtil.toHexString(dataBytes) + ", dataStr = " + data);
        }

        data = data.replace("\r", "");
        data = data.replace("\n", "");

        AtCommandResult result = mAtParser.process(data);

        try {
            write(result.toString().getBytes());
        } catch (Exception e) {
            Log.d(TAG, "Exception in processAtCommands: " + e);
            e.printStackTrace();
        }
    }

    private void onSerialRead(byte[] data) {
        if (mListener != null)
            mListener.onSerialRead(data, null, null);
    }

    @Override
    public void run() { // connect & read
        Exception sendIoErrorException = null;

        try {
            mSocket = mDevice.createRfcommSocketToServiceRecord(BLUETOOTH_HFP);
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

                onSerialRead(data);
            }
        } catch (Exception e) {
            Log.d(TAG, "Exception in HFP run loop: " + e);
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
