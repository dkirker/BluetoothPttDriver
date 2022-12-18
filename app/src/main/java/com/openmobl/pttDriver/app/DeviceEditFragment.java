package com.openmobl.pttDriver.app;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.datepicker.MaterialTextInputPicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.openmobl.pttDriver.db.DriverDatabase;
import com.openmobl.pttDriver.db.DriverSQLiteDatabase;
import com.openmobl.pttDriver.model.Device;
import com.openmobl.pttDriver.model.Driver;
import com.openmobl.pttDriver.model.ModelDataAction;
import com.openmobl.pttDriver.R;
import com.openmobl.pttDriver.model.PttDriver;

import java.util.ArrayList;
import java.util.List;

public class DeviceEditFragment extends DialogFragment {
    public static final String TAG = DeviceEditFragment.class.getName();

    private static final String ARGUMENT_DEVICE = "device";
    private static final String ARGUMENT_ACTION = "action";

    private static final String SELECT_DEVICE_ACTION = "android.bluetooth.devicepicker.action.LAUNCH";
    private static final String SELECT_DEVICE_SELECTED = "android.bluetooth.devicepicker.action.DEVICE_SELECTED";


    public interface DeviceEditListener {
        void onDeviceEdit(ModelDataAction action, Device device);
    }

    private MaterialButton mDeviceButton;
    private TextView mDeviceLabel;
    private TextView mDeviceMac;
    private AutoCompleteTextView mDriverDropdown;
    private MaterialCheckBox mAutoConnect;
    private MaterialCheckBox mAutoReconnect;
    private TextInputLayout mPttDownKeyDelay;

    private TextView mDeviceNameLabel;
    private boolean mAdvancedMode = false;
    private int mAdvancedModeCountdown = 3;
    private TextInputLayout mDeviceNameInput;
    private TextInputLayout mDeviceMacInput;

    private boolean mDeviceSelected = false;

    private BluetoothDevice mPttDevice;
    private BroadcastReceiver mDevicePicker;
    private Driver mDriver;
    private List<Driver> mDrivers;

    private DriverDatabase mDb;

    private DeviceEditListener mListener;

    public static DialogFragment createDialog(Context context, Device device, ModelDataAction action) {
        Bundle args = new Bundle();

        args.putParcelable(ARGUMENT_DEVICE, device);
        args.putInt(ARGUMENT_ACTION, action.ordinal());

        return (DialogFragment)Fragment.instantiate(context, DeviceEditFragment.class.getName(), args);
    }

    private Device getDevice() {
        return getArguments().getParcelable(ARGUMENT_DEVICE);
    }

    private ModelDataAction getAction() {
        return ModelDataAction.values()[getArguments().getInt(ARGUMENT_ACTION)];
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DriverSQLiteDatabase(getActivity());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mListener = (DeviceEditListener)activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DriverEditListener!");
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        ((AlertDialog)getDialog()).getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validate()) {
                    Device device = createDevice();
                    mListener.onDeviceEdit(getAction(), device);
                    dismiss();
                } else {
                    // TODO: Display error
                }
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        String actionName;
        switch (getAction()) {
            case ADD:
                actionName = getString(R.string.add);
                break;
            case EDIT:
                actionName = getString(R.string.update);
                break;
            default:
                throw new RuntimeException("Unknown action " + getAction());
        }
        builder.setPositiveButton(actionName, null);
        builder.setNegativeButton(android.R.string.cancel, null);

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View view = inflater.inflate(R.layout.edit_device, null, false);

        mDevicePicker = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    String action = intent.getAction();

                    if (SELECT_DEVICE_SELECTED.equals(action)) {
                        deviceSelected(intent);
                    }
                }
                context.unregisterReceiver(mDevicePicker);
            }
        };

        mDeviceNameLabel = (TextView)view.findViewById(R.id.editdevice_label_pttDevice);
        mDeviceLabel = (TextView)view.findViewById(R.id.editdevice_label_pttDeviceName);
        mDeviceButton = (MaterialButton)view.findViewById(R.id.editdevice_button_pttDevice);
        mDeviceMac = (TextView)view.findViewById(R.id.editdevice_label_pttDeviceMac);
        mDriverDropdown = (AutoCompleteTextView)view.findViewById(R.id.editdevice_dropdown_pttDriverSelect);
        mAutoConnect = (MaterialCheckBox)view.findViewById(R.id.editdevice_checkBox_autoConnect);
        mAutoReconnect = (MaterialCheckBox)view.findViewById(R.id.editdevice_checkBox_autoReonnect);
        mPttDownKeyDelay = (TextInputLayout)view.findViewById(R.id.editdevice_input_pttDevicePttDownKeyDelay);

        mDeviceNameInput = (TextInputLayout)view.findViewById(R.id.editdevice_input_pttDeviceName);
        mDeviceMacInput = (TextInputLayout)view.findViewById(R.id.editdevice_input_pttDeviceMac);

        mDeviceNameLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAdvancedModeCountdown == 0 && !mAdvancedMode) {
                    mAdvancedMode = true;

                    if (mDeviceNameInput.getEditText() != null) {
                        mDeviceNameInput.getEditText().setText(mDeviceLabel.getText());
                    }
                    mDeviceNameInput.setVisibility(View.VISIBLE);
                    mDeviceLabel.setVisibility(View.GONE);

                    if (mDeviceMacInput.getEditText() != null) {
                        mDeviceMacInput.getEditText().setText(mDeviceMac.getText());
                    }
                    mDeviceMacInput.setVisibility(View.VISIBLE);
                    mDeviceMac.setVisibility(View.GONE);
                } else {
                    mAdvancedModeCountdown--;
                }
            }
        });

        mDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectDevice();
            }
        });

        mDrivers = getAllDrivers();

        if (mDrivers != null && mDrivers.size() > 0) {
            ArrayAdapter<Driver> driverDropdownAdapter = new ArrayAdapter<>(requireContext(), R.layout.driver_list_item, mDrivers);
            mDriverDropdown.setAdapter(driverDropdownAdapter);
            mDriverDropdown.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mDriver = mDrivers.get(position);

                    try {
                        PttDriver driverObj = new PttDriver(requireContext(), mDriver.getJson());

                        if (mPttDownKeyDelay.getEditText() != null) {
                            mPttDownKeyDelay.getEditText().setText(Integer.toString(driverObj.getReadObj().getDefaultPttDownKeyDelay()));
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Driver JSON: " + mDriver.getJson());
                        e.printStackTrace();
                    }
                }
            });
        } else {
            TextView error = (TextView)view.findViewById(R.id.editdevice_label_pttNoDriversLabel);

            error.setVisibility(View.VISIBLE);
            //mDriverDropdown.setInputType();

            mDriver = Driver.getEmptyDriver();
        }

        Device old = getDevice();
        if (old != null) {
            mDeviceLabel.setText(old.getName());
            mDeviceMac.setText(old.getMacAddress());
            mDeviceSelected = true;
            mDriver = lookupDriver(old.getDriverId());
            mAutoConnect.setChecked(old.getAutoConnect());
            mAutoReconnect.setChecked(old.getAutoReconnect());
            if (mPttDownKeyDelay.getEditText() != null)
                mPttDownKeyDelay.getEditText().setText(Integer.toString(old.getPttDownDelay()));
            if (mDriver.getId() != -1) {
                mDriverDropdown.setText(mDriver.getName(), false);
            }
        } else {
            mAutoConnect.setChecked(Device.getAutoConnectDefault());
            mAutoReconnect.setChecked(Device.getAutoReconnectDefault());
            if (mPttDownKeyDelay.getEditText() != null)
                mPttDownKeyDelay.getEditText().setText(Integer.toString(Device.getPttDownDelayDefault()));
        }

        // Fixes issues with text colour on light themes with pre-honeycomb devices.
        //builder.setInverseBackgroundForced(true);

        builder.setTitle(getAction() == ModelDataAction.EDIT ? R.string.add_device : R.string.edit_device);

        builder.setView(view);

        return builder.create();
    }

    private Driver lookupDriver(int id) {
        if (id >= 0) {
            Driver driver = mDb.getDriver(id);

            return (driver != null) ? driver : Driver.getEmptyDriver();
        } else {
            return Driver.getEmptyDriver();
        }
    }

    private List<Driver> getAllDrivers() {
        return mDb.getDrivers();
    }

    private boolean validate() {
        // We assume that mDeviceMac has a value because that gets filled with mDeviceLabl.
        // The mDeviceLabel gets populated either from prior device or selecting a new device.
        boolean deviceValid = mDeviceSelected;
        boolean driverValid = mDriver != null;
        boolean inputValid = mPttDownKeyDelay.getEditText() != null &&
                mPttDownKeyDelay.getEditText().getText() != null &&
                !mPttDownKeyDelay.getEditText().getText().toString().isEmpty();

        if (mAdvancedMode) {
            if (mDeviceNameInput.getEditText() != null && mDeviceMacInput.getEditText() != null) {
                String mac = mDeviceMacInput.getEditText().getText().toString();

                deviceValid = !mDeviceNameInput.getEditText().getText().toString().isEmpty() &&
                        !mac.isEmpty() && BluetoothAdapter.checkBluetoothAddress(mac);
            }
        }

        return deviceValid && driverValid && inputValid;
    }

    private Device createDevice() {
        Device old = getDevice();
        int id = -1;
        String name = "";
        String mac = "";
        int driverId = -1;
        String driverName = "";
        boolean autoConnect = mAutoConnect.isChecked();
        boolean autoReconnect = mAutoReconnect.isChecked();
        int pttDownKeyDelay = 0;

        if (mPttDownKeyDelay.getEditText() != null) {
            pttDownKeyDelay = Integer.parseInt(mPttDownKeyDelay.getEditText().getText().toString());
        }

        if (old != null) {
            id = old.getId();
            name = old.getName();
            mac = old.getMacAddress();
        }

        if (mDriver != null) {
            driverId = mDriver.getId();
            driverName = mDriver.getName();
        }

        if (mPttDevice != null) {
            name = mPttDevice.getName();
            mac = mPttDevice.getAddress();
        }

        if (mAdvancedMode) {
            if (mDeviceNameInput.getEditText() != null) {
                name = mDeviceNameInput.getEditText().getText().toString();
            }
            if (mDeviceMacInput.getEditText() != null) {
                mac = mDeviceMacInput.getEditText().getText().toString();
            }
        }

        return new Device(id, name, mac, driverId, driverName,
                        autoConnect, autoReconnect, pttDownKeyDelay);
    }

    private void selectDevice() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SELECT_DEVICE_SELECTED);
        requireContext().registerReceiver(mDevicePicker, filter);

        Intent intent = new Intent(SELECT_DEVICE_ACTION);
        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
        //mActivityResultLauncher.launch(intent);
    }
    private void deviceSelected(Intent resultData) {
        if (resultData != null) {
            mPttDevice = resultData.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (mPttDevice != null) {
                Log.d(TAG, "Selected device: " + mPttDevice.getName());

                mDeviceLabel.setText(mPttDevice.getName());
                mDeviceMac.setText(mPttDevice.getAddress());

                if (mAdvancedMode) {
                    if (mDeviceNameInput.getEditText() != null) {
                        mDeviceNameInput.getEditText().setText(mPttDevice.getName());
                    }
                    if (mDeviceMacInput.getEditText() != null) {
                        mDeviceMacInput.getEditText().setText(mPttDevice.getAddress());
                    }
                }

                mDeviceSelected = true;
            }
        }
    }
}
