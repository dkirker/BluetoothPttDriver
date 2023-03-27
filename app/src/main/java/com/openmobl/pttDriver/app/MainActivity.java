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
import com.openmobl.pttDriver.model.PttDriver;
import com.openmobl.pttDriver.model.Record;
import com.openmobl.pttDriver.service.DeviceConnectionState;
import com.openmobl.pttDriver.service.DeviceDriverServiceManager;
import com.openmobl.pttDriver.service.DeviceDriverServiceManager.DeviceDriverServiceHolder;
import com.openmobl.pttDriver.service.DeviceStatusListener;
import com.openmobl.pttDriver.service.IDeviceDriverService;

import com.openmobl.pttDriver.databinding.ActivityMainBinding;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.openmobl.pttDriver.utils.ServiceUtils;

import java.util.List;
import java.util.Map;

//@RequiresApi(api = Build.VERSION_CODES.P)
public class MainActivity extends AppCompatActivity implements DeviceStatusListener,
        DriverEditFragment.DriverEditListener, DeviceEditFragment.DeviceEditListener {
    private static final String TAG = MainActivity.class.getName();

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

    private DeviceDriverServiceManager mDeviceServiceManager;

    /*private IDeviceDriverService mService;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG, "onServiceConnected");
            mService = ((DeviceDriverServiceBinder)service).getService();

            mService.registerStatusListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG, "onServiceDisconnected");
            mService = null;
        }
    };*/

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

        if (!hasPermissions()) {
            requestPermissions();
        } else {
            mHasPermissions = true;
        }

        mEfabAdd = findViewById(R.id.efab_add);
        mFabAddDevice = findViewById(R.id.fab_addDevice);
        mTextAddDevice = findViewById(R.id.text_addDevice);
        mFabAddDriver = findViewById(R.id.fab_addDriver);
        mTextAddDriver = findViewById(R.id.text_addDriver);

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

        mDeviceServiceManager = new DeviceDriverServiceManager();

        /*Intent connectIntent = new Intent(this, BluetoothDeviceDriverService.class);
        bindService(connectIntent, mConnection, 0);*/
    }

    // TODO: Reorg
    private IDeviceDriverService createAndBindService(int deviceId, Class<?> cls) {
        mDeviceServiceManager.createService(deviceId, this);

        ServiceUtils.startService(cls, getApplicationContext());

        Intent connectIntent = new Intent(this, cls);
        bindService(connectIntent, mDeviceServiceManager.getConnection(deviceId), 0);

        return mDeviceServiceManager.getService(deviceId);
    }

    private void unbindAllServices() {
        Map<Integer, DeviceDriverServiceHolder> services = mDeviceServiceManager.getAllServices();

        for (Map.Entry<Integer, DeviceDriverServiceHolder> service: services.entrySet()) {
            DeviceDriverServiceHolder holder = service.getValue();

            if (holder != null && holder.getService() != null) {
                holder.getService().unregisterStatusListener(this);
                unbindService(holder.getConnection());
            }
        }
    }

    private void reregisterAllListeners() {
        Map<Integer, DeviceDriverServiceHolder> services = mDeviceServiceManager.getAllServices();

        for (Map.Entry<Integer, DeviceDriverServiceHolder> service: services.entrySet()) {
            mDeviceServiceManager.recreateService(service.getKey(), this);
            /*DeviceDriverServiceHolder holder = service.getValue();

            if (holder != null && holder.getService() != null) {
                holder.getService().registerStatusListener(this);
            }*/
        }
    }
    private void unregisterAllListeners() {
        Map<Integer, DeviceDriverServiceHolder> services = mDeviceServiceManager.getAllServices();

        for (Map.Entry<Integer, DeviceDriverServiceHolder> service: services.entrySet()) {
            DeviceDriverServiceHolder holder = service.getValue();

            if (holder != null && holder.getService() != null) {
                holder.getService().unregisterStatusListener(this);
            }
        }
    }

    private IDeviceDriverService getService(int deviceId) {
        return mDeviceServiceManager.getService(deviceId);
    }

    private ServiceConnection getConnection(int deviceId) {
        return mDeviceServiceManager.getConnection(deviceId);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");

        /*if (mService != null)
            mService.unregisterStatusListener(this);

        unbindService(mConnection);*/

        // TODO: Migrate
        unbindAllServices();

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");

        super.onResume();

        /*if (mService != null)
            mService.registerStatusListener(this);*/
        /*
        Intent connectIntent = new Intent(this, BluetoothDeviceDriverService.class);
        bindService(connectIntent, mConnection, 0);
        */

        // TODO: Migrate
        reregisterAllListeners();
    }

    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");

        super.onPause();

        /*if (mService != null)
            mService.unregisterStatusListener(this);

        //unbindService(mConnection);*/

        // TODO: Migrate
        unregisterAllListeners();
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

    /*private void connectToDevice(Device device) {
        if (device != null) {
            if (mService != null) {
                Driver driver = null;
                PttDriver pttDriver = null;

                if (mService.getConnectionState() != DeviceConnectionState.Disconnected) {
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

                if (driver != null) {
                    try {
                        pttDriver = new PttDriver(getApplicationContext(), driver.getJson());
                    } catch (Exception e) {
                        e.printStackTrace();

                        Log.d(TAG, "Received exception opening \"" + driver.getName() + "\": " + e);
                        Log.v(TAG, "Driver JSON: " + driver.getJson());

                        // Show error
                        return;
                    }
                } else {
                    // Show error
                    return;
                }

                mService.setPttDevice(device);
                if (mService.deviceIsValid()) {
                    mService.setPttDriver(null);

                    mService.setAutomaticallyReconnect(device.getAutoReconnect());

                    mService.setPttDriver(pttDriver);

                    mService.connect();
                } else {
                    // Show error
                }
            } else {
                // Show error
            }
        } else {
            // Show error
        }
    }*/

    private void connectToDevice(final Device device) {
        if (device != null) {
            Driver driver = null;
            PttDriver pttDriver = null;
            IDeviceDriverService service = mDeviceServiceManager.getService(device.getId());

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

            if (driver != null) {
                try {
                    pttDriver = new PttDriver(getApplicationContext(), driver.getJson());
                } catch (Exception e) {
                    e.printStackTrace();

                    Log.d(TAG, "Received exception opening \"" + driver.getName() + "\": " + e);
                    Log.v(TAG, "Driver JSON: " + driver.getJson());

                    // Show error
                    return;
                }
            } else {
                // Show error
                return;
            }

            if (service == null ||
                    service.getClass() != DeviceDriverServiceManager.getServiceClassForDeviceType(device.getDeviceType(), pttDriver.getType())) {

                if (service != null && service.getConnectionState() != DeviceConnectionState.Disconnected) {
                    service.disconnect();
                }
                createAndBindService(device.getId(),
                        DeviceDriverServiceManager.getServiceClassForDeviceType(device.getDeviceType(), pttDriver.getType()));

                // Re-run with a delay to give the service time to connect
                final Handler handler = new Handler(getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connectToDevice(device);
                    }
                }, 500);

                return;
            }

            if (service != null) {
                if (service.getConnectionState() != DeviceConnectionState.Disconnected) {
                    service.disconnect();
                }

                service.setPttDevice(device);
                if (service.deviceIsValid()) {
                    service.setPttDriver(null);

                    service.setAutomaticallyReconnect(device.getAutoReconnect());

                    service.setPttDriver(pttDriver);

                    service.connect();
                } else {
                    // Show error
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
                /*if (mService != null)
                    mService.connect();*/
                break;
            case R.id.menu_action_bt_disconnect:
                /*if (mService != null)
                    mService.disconnect();*/
                break;
            default:
        }
        return true;
    }

    @Override
    public void onStatusMessageUpdate(String message) {
        /*runOnUiThread(new Runnable() {
            public void run() {
                mStatusText.setText(message);
            }
        });*/
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
