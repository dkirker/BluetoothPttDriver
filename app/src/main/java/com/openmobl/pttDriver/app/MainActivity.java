package com.openmobl.pttDriver.app;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.openmobl.pttDriver.BuildConfig;
import com.openmobl.pttDriver.R;

import com.openmobl.pttDriver.app.ui.main.DeviceOrDriverViewModel;
import com.openmobl.pttDriver.app.ui.main.DevicesViewModel;
import com.openmobl.pttDriver.app.ui.main.DriversViewModel;
import com.openmobl.pttDriver.app.ui.main.SectionsPagerAdapter;
import com.openmobl.pttDriver.db.DriverDatabase;
import com.openmobl.pttDriver.db.DriverSQLiteDatabase;
import com.openmobl.pttDriver.model.Device;
import com.openmobl.pttDriver.model.Driver;
import com.openmobl.pttDriver.model.ModelDataAction;
import com.openmobl.pttDriver.model.ModelDataActionEventListener;
import com.openmobl.pttDriver.model.PttDriver;
import com.openmobl.pttDriver.model.Record;
import com.openmobl.pttDriver.service.DeviceDriverService;
import com.openmobl.pttDriver.service.IDeviceDriverService;

import com.openmobl.pttDriver.databinding.ActivityMainBinding;

import android.Manifest;
import android.app.Activity;
//import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResult;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.openmobl.pttDriver.utils.ServiceUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

//@RequiresApi(api = Build.VERSION_CODES.P)
public class MainActivity extends AppCompatActivity implements DeviceDriverService.DeviceStatusListener,
        DriverEditFragment.DriverEditListener, DeviceEditFragment.DeviceEditListener {
    private static final String TAG = MainActivity.class.getName();

    private static final String SELECT_DEVICE_ACTION = "android.bluetooth.devicepicker.action.LAUNCH";
    private static final String SELECT_DEVICE_SELECTED = "android.bluetooth.devicepicker.action.DEVICE_SELECTED";

    private final String[] PERMISSIONS = {
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private Menu mMenu;
    private MenuItem mMenuConnect;
    private MenuItem mMenuDisconnect;
    private MaterialButton mDeviceButton;
    private TextView mDeviceLabel;
    private MaterialButton mDriverButton;
    private TextView mDriverLabel;
    private TextView mStatusText;
    private CheckBox mAutoConnect;
    private CheckBox mAutoReconnect;
    private TextInputEditText mPttDownKeyDelayInput;
    private ExtendedFloatingActionButton mEfabAdd;
    private FloatingActionButton mFabAddDevice;
    private TextView mTextAddDevice;
    private FloatingActionButton mFabAddDriver;
    private TextView mTextAddDriver;

    private ActivityMainBinding mBinding;

    private boolean mHasPermissions = false;

    private DriverDatabase mDatabase;

    private DevicesViewModel mDevicesViewModel;
    private DriversViewModel mDriversViewModel;

    private Uri mPttDriverPath;
    private PttDriver mPttDriver;
    private BluetoothDevice mPttDevice;
    private IDeviceDriverService mService;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "onServiceConnected");
            mService = ((DeviceDriverService.DeviceDriverBinder) service).getService();

            mService.registerStatusListener(MainActivity.this);
            //mService.setConnectOnComplete(true);
            //mService.setAutomaticallyReconnect(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG, "onServiceDisconnected");
            mService = null;
        }
    };

    private BroadcastReceiver mDevicePicker;
    private ActivityResultLauncher<Intent> mActivityResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result != null && result.getResultCode() == Activity.RESULT_OK) {
                                Intent intent = result.getData();

                                driverSelected(intent);
                                /*switch (intent.getAction()) {
                                    case SELECT_DEVICE_SELECTED:
                                        deviceSelected(intent);
                                        break;
                                    case SELECT_DRIVER_SELECTED:
                                        driverSelected(intent);
                                        break;
                                }*/
                            }
                        }
                    });
    private ActivityResultLauncher<String[]> mPermissionsRequest =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    new ActivityResultCallback<Map<String, Boolean>>() {
                        @Override
                        public void onActivityResult(Map<String, Boolean> result) {
                            Log.d(TAG, "Permissions granted: " + result);
                            boolean isGranted = true; // Assume true, check for false

                            for (Map.Entry<String, Boolean> permission: result.entrySet()) {
                                Log.d(TAG, "Permission: " + permission.getKey() +
                                        " - " + permission.getValue());

                                if (!permission.getValue())
                                    isGranted = false;
                            }

                            mHasPermissions = isGranted;
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");

        super.onCreate(savedInstanceState);

        mDatabase = new DriverSQLiteDatabase(getApplicationContext());

        mDriversViewModel = new ViewModelProvider(this).get(DriversViewModel.class);
        mDriversViewModel.setDataSource(mDatabase);
        mDriversViewModel.refreshSource();
        mDriversViewModel.getDataEvent().observe(this, event -> {
                if (event != null) {
                    Record record = event.getParcelable(DeviceOrDriverViewModel.ARG_DATAEVENT_RECORD);
                    ModelDataAction action = ModelDataAction.valueOf(event.getString(DeviceOrDriverViewModel.ARG_DATAEVENT_ACTION));

                    switch (action) {
                        case ADD:
                        case EDIT:
                            addOrEditDriver((Driver)record, action);
                            break;
                        case DELETE:
                            mDatabase.removeDriver((Driver)record);
                            break;
                        default:
                            break;
                    }
                }
            });

        mDevicesViewModel = new ViewModelProvider(this).get(DevicesViewModel.class);
        mDevicesViewModel.setDataSource(mDatabase);
        mDevicesViewModel.refreshSource();
        mDevicesViewModel.getDataEvent().observe(this, event -> {
                if (event != null) {
                    Record record = event.getParcelable(DeviceOrDriverViewModel.ARG_DATAEVENT_RECORD);
                    ModelDataAction action = ModelDataAction.valueOf(event.getString(DeviceOrDriverViewModel.ARG_DATAEVENT_ACTION));

                    switch (action) {
                        case ADD:
                        case EDIT:
                            addOrEditDevice((Device)record, action);
                            break;
                        case CONNECT:
                            connectToDevice((Device)record);
                            break;
                        case DELETE:
                            mDatabase.removeDevice((Device)record);
                            break;
                        default:
                            break;
                    }
                }
            });

        //setContentView(R.layout.activity_main);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        ViewPager viewPager = mBinding.viewPager;
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = mBinding.tabs;
        tabs.setupWithViewPager(viewPager);

        ServiceUtils.startService(getApplicationContext());

        mDevicePicker = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null) {
                    String action = intent.getAction();

                    if (SELECT_DEVICE_SELECTED.equals(action)) {
                        deviceSelected(intent);
                    }
                }
                unregisterReceiver(mDevicePicker);
            }
        };

        if (!hasPermissions()) {
            requestPermissions();
        } else {
            mHasPermissions = true;
        }

        mDeviceButton = (MaterialButton)findViewById(R.id.button_pttDevice);
        mDeviceLabel = (TextView)findViewById(R.id.label_pttDevice);
        mDriverButton = (MaterialButton)findViewById(R.id.button_pttDriver);
        mDriverLabel = (TextView)findViewById(R.id.label_pttDriver);
        mStatusText = (TextView)findViewById(R.id.label_status_text);
        mAutoConnect = (CheckBox)findViewById(R.id.checkBox_pttDeviceAutoConnect);
        mAutoReconnect = (CheckBox)findViewById(R.id.checkBox_pttDeviceAutoReconnect);
        mPttDownKeyDelayInput = (TextInputEditText)findViewById(R.id.input_pttDevicePttDownKeyDelay);
        mEfabAdd = (ExtendedFloatingActionButton) findViewById(R.id.efab_add);
        mFabAddDevice = (FloatingActionButton) findViewById(R.id.fab_addDevice);
        mTextAddDevice = (TextView) findViewById(R.id.text_addDevice);
        mFabAddDriver = (FloatingActionButton) findViewById(R.id.fab_addDriver);
        mTextAddDriver = (TextView) findViewById(R.id.text_addDriver);

        mDeviceButton.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                selectDevice();
             }
         });
        mDriverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectDriver();
            }
        });
        mAutoConnect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mService != null) {
                    mService.setConnectOnComplete(isChecked);
                }
            }
        });
        mAutoReconnect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mService != null) {
                    mService.setAutomaticallyReconnect(isChecked);
                }
            }
        });
        mPttDownKeyDelayInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().isEmpty() && mService != null) {
                    mService.setPttDownKeyDelay(Integer.parseInt(s.toString()));
                }
            }
        });

        mEfabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAddFab();
            }
        });
        mFabAddDriver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAddFab();
                addDriver();
            }
        });
        mFabAddDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAddFab();
                addDevice();
            }
        });


        toggleAddFab(true);

        Intent connectIntent = new Intent(this, DeviceDriverService.class);
        bindService(connectIntent, mConnection, 0);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");

        if (mService != null)
            mService.unregisterStatusListener(this);

        unbindService(mConnection);

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");

        super.onResume();

        if (mService != null)
            mService.registerStatusListener(this);
        /*
        Intent connectIntent = new Intent(this, DeviceDriverService.class);
        bindService(connectIntent, mConnection, 0);
        */
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");

        super.onPause();

        if (mService != null)
            mService.unregisterStatusListener(this);

        //unbindService(mConnection);
    }

    private void toggleAddFab() { toggleAddFab(false); }
    private void toggleAddFab(boolean forceHide) {
        if (forceHide || mEfabAdd.isExtended()) {
            mEfabAdd.shrink();
            mFabAddDevice.hide();
            mTextAddDevice.setVisibility(View.GONE);
            mFabAddDriver.hide();
            mTextAddDriver.setVisibility(View.GONE);
        } else {
            mEfabAdd.extend();
            mFabAddDriver.show();
            mTextAddDriver.setVisibility(View.VISIBLE);
            mFabAddDevice.show();
            mTextAddDevice.setVisibility(View.VISIBLE);
        }
    }

    private void requestPermissions() {
        mPermissionsRequest.launch(PERMISSIONS);
    }
    private boolean hasPermissions() {
        for (String permission : PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission not granted: " + permission);
                return false;
            }
            Log.d(TAG, "Permission granted: " + permission);
        }
        Log.d(TAG, "All permissions granted as requested");
        return true;
    }

    private void selectDevice() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SELECT_DEVICE_SELECTED);
        registerReceiver(mDevicePicker, filter);

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

                if (mService != null)
                    mService.setPttDevice(mPttDevice);
            }
        }
    }

    private void selectDriver() {
        Intent filePicker = new Intent(Intent.ACTION_GET_CONTENT);
        filePicker.addCategory(Intent.CATEGORY_OPENABLE);
        filePicker.setType("*/*");
        mActivityResultLauncher.launch(Intent.createChooser(filePicker,
                                            getString(R.string.select_driver)));
    }
    private void driverSelected(Intent resultData) {
        if (resultData != null) {
            mPttDriverPath = resultData.getData();

            try {
                mPttDriver = new PttDriver(getApplicationContext(), mPttDriverPath);

                Log.d(TAG, "Parsed PttDriver:\n" + mPttDriver);

                if (mPttDriver.isValid()) {
                    mDriverLabel.setText(mPttDriver.getDriverName());

                    if (mService != null)
                        mService.setPttDriver(mPttDriver);
                    // else do something......
                } else {
                    onStatusMessageUpdate(getString(R.string.status_invalid_driver));
                }
            } catch (Exception e) {
                Log.d(TAG, "Failed to open driver file. Received exception: " + e);
                onStatusMessageUpdate("Failed to open driver file. Received exception: " + e);
                e.printStackTrace();
            }
        }
    }

    private void addDriver() {
        addOrEditDriver(null, ModelDataAction.ADD);
    }

    private void addOrEditDriver(Driver driver, ModelDataAction action) {
        DialogFragment dialog = DriverEditFragment.createDialog(MainActivity.this, driver, action);
        dialog.show(getSupportFragmentManager(), DriverEditFragment.TAG);
    }

    @Override
    public void onDriverEdit(ModelDataAction action, Driver driver) {
        Log.v(TAG, "onDriverEdit " + action + " on Driver: " + driver.toStringFull());

        mDatabase.addOrUpdateDriver(driver);
        mDriversViewModel.refreshSource();
        mDevicesViewModel.refreshSource();
    }

    private void addDevice() {
        addOrEditDevice(null, ModelDataAction.ADD);
    }

    private void addOrEditDevice(Device device, ModelDataAction action) {
        DialogFragment dialog = DeviceEditFragment.createDialog(MainActivity.this, device, action);
        dialog.show(getSupportFragmentManager(), DeviceEditFragment.TAG);
    }

    private void connectToDevice(Device device) {
        // get driver
        // set options for DeviceDriverService
        // connect

        if (mService != null) {
            Driver driver = null;
            PttDriver pttDriver = null;
            BluetoothDevice pttDevice = null;

            if (mService.getConnected() != DeviceDriverService.Connected.False) {
                mService.disconnect();
            }

            if (device.getDriverId() != -1) {
                driver = mDatabase.getDriver(device.getDriverId());
            } else {
                List<Driver> drivers = mDatabase.getDrivers();

                for (Driver test : drivers) {
                    if (test.getDeviceNameMatch() != null) {
                        if (device.getName().contains(test.getDeviceNameMatch())) {
                            driver = test;
                        }
                    }
                }
            }

            BluetoothManager btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter btAdapter = btManager.getAdapter();
            //Set<BluetoothDevice> btDevices = btAdapter.getBondedDevices();

            /*for (BluetoothDevice btDevice : btDevices) {
                if (btDevice.getAddress().contains(device.getMacAddress())) {
                    pttDevice = btDevice;
                }
            }*/
            pttDevice = btAdapter.getRemoteDevice(device.getMacAddress());

            if (pttDevice != null) {
                if (driver != null) {
                    try {
                        pttDriver = new PttDriver(getApplicationContext(), driver.getJson());
                    } catch (Exception e) {
                        e.printStackTrace();

                        Log.d(TAG, "Received exception opening \"" + driver.getName() + "\": " + e);
                        Log.v(TAG, "Driver JSON: " + driver.getJson());

                        // Show error
                    }
                } else {
                    // Show error
                }

                if (pttDriver != null) {
                    mService.setPttDriver(null);
                    mService.setPttDevice(null);

                    //mService.setConnectOnComplete(device.getAutoConnect());
                    mService.setAutomaticallyReconnect(device.getAutoReconnect());

                    mService.setPttDriver(pttDriver);
                    mService.setPttDevice(pttDevice);

                    mService.connect();
                }
            } else {
                // Show error
            }
        } else {
            // Show error
        }
    }

    @Override
    public void onDeviceEdit(ModelDataAction action, Device device) {
        Log.v(TAG, "onDeviceEdit " + action + " on Device: " + device.toStringFull());

        mDatabase.addOrUpdateDevice(device);
        mDevicesViewModel.refreshSource();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);

        mMenuConnect = mMenu.findItem(R.id.menu_action_bt_connect);
        mMenuDisconnect = mMenu.findItem(R.id.menu_action_bt_disconnect);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_action_about_app:
                showAboutDialog();
                break;
            case R.id.menu_action_bt_settings:
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivity(intent);
                break;
            case R.id.menu_action_bt_connect:
                if (mService != null)
                    mService.connect();
                break;
            case R.id.menu_action_bt_disconnect:
                if (mService != null)
                    mService.disconnect();
                break;
            default:
        }
        return true;
    }

    @Override
    public void onStatusMessageUpdate(String message) {
        runOnUiThread(new Runnable() {
            public void run() {
                mStatusText.setText(message);
            }
        });
    }
    @Override
    public void onConnected() {
        Log.d(TAG, "onConnected");

        runOnUiThread(new Runnable() {
            public void run() {
                mMenuConnect.setVisible(false);
                mMenuDisconnect.setVisible(true);
            }
        });
    }
    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");

        runOnUiThread(new Runnable() {
            public void run() {
                mMenuConnect.setVisible(true);
                mMenuDisconnect.setVisible(false);
            }
        });
    }
    @Override
    public void onBatteryEvent(byte level) {
        Log.v(TAG, "Battery: " + level + "%");
    }

    private void showAboutDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(Html.fromHtml(getString(R.string.about_dialog_title, getString(R.string.app_name), BuildConfig.VERSION_NAME)))
                .setMessage(Html.fromHtml(getString(R.string.about_dialog_body)))
                .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing
                    }
                })
                .create();
        dialog.show();

        // make links clickable:
        ((TextView)dialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    /*
    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    */
}
