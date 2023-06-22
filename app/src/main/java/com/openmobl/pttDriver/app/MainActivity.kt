package com.openmobl.pttDriver.app

import android.Manifest
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.openmobl.pttDriver.BuildConfig
import com.openmobl.pttDriver.R
import com.openmobl.pttDriver.app.DeviceEditFragment.DeviceEditListener
import com.openmobl.pttDriver.app.DriverEditFragment.DriverEditListener
import com.openmobl.pttDriver.app.ui.main.DeviceOrDriverViewModel
import com.openmobl.pttDriver.app.ui.main.DevicesViewModel
import com.openmobl.pttDriver.app.ui.main.DriversViewModel
import com.openmobl.pttDriver.app.ui.main.SectionsPagerAdapter
import com.openmobl.pttDriver.databinding.ActivityMainBinding
import com.openmobl.pttDriver.db.DriverDatabase
import com.openmobl.pttDriver.db.DriverSQLiteDatabase
import com.openmobl.pttDriver.model.*
import com.openmobl.pttDriver.service.DeviceConnectionState
import com.openmobl.pttDriver.service.DeviceDriverServiceManager
import com.openmobl.pttDriver.service.DeviceStatusListener
import com.openmobl.pttDriver.service.IDeviceDriverService
import com.openmobl.pttDriver.utils.ServiceUtils

//@RequiresApi(api = Build.VERSION_CODES.P)
class MainActivity : AppCompatActivity(), DeviceStatusListener, DriverEditListener,
    DeviceEditListener {
    private val PERMISSIONS = arrayOf(
        Manifest.permission.FOREGROUND_SERVICE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    private var mMenu: Menu? = null
    private var mMenuConnect: MenuItem? = null
    private var mMenuDisconnect: MenuItem? = null
    private var mEfabAdd: ExtendedFloatingActionButton? = null
    private var mFabAddDevice: FloatingActionButton? = null
    private var mTextAddDevice: TextView? = null
    private var mFabAddDriver: FloatingActionButton? = null
    private var mTextAddDriver: TextView? = null
    private var mBinding: ActivityMainBinding? = null
    private var mHasPermissions = false
    private var mDatabase: DriverDatabase? = null
    private var mDevicesViewModel: DevicesViewModel? = null
    private var mDriversViewModel: DriversViewModel? = null
    private var mDeviceServiceManager: DeviceDriverServiceManager? = null

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
    private val mPermissionsRequest =
        registerForActivityResult<Array<String>, Map<String, Boolean>>(
            RequestMultiplePermissions(),
            object : ActivityResultCallback<Map<String?, Boolean?>?> {
                override fun onActivityResult(result: Map<String, Boolean>) {
                    Log.d(TAG, "Permissions granted: $result")
                    var isGranted = true // Assume true, check for false
                    for ((key, value) in result) {
                        Log.d(
                            TAG, "Permission: " + key +
                                    " - " + value
                        )
                        if (!value) isGranted = false
                    }
                    mHasPermissions = isGranted
                }
            })

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.v(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        mDatabase = DriverSQLiteDatabase(applicationContext)
        mDriversViewModel = ViewModelProvider(this).get(DriversViewModel::class.java)
        mDriversViewModel!!.setDataSource(mDatabase)
        mDriversViewModel!!.refreshSource()
        mDriversViewModel.getDataEvent().observe(this) { event: Bundle? ->
            if (event != null) {
                val record: Record =
                    event.getParcelable(DeviceOrDriverViewModel.Companion.ARG_DATAEVENT_RECORD)
                val action =
                    ModelDataAction.valueOf(event.getString(DeviceOrDriverViewModel.Companion.ARG_DATAEVENT_ACTION)!!)
                when (action) {
                    ModelDataAction.ADD, ModelDataAction.EDIT -> addOrEditDriver(
                        record as Driver,
                        action
                    )
                    ModelDataAction.DELETE -> mDatabase.removeDriver(record as Driver)
                    else -> {}
                }
            }
        }
        mDevicesViewModel = ViewModelProvider(this).get(DevicesViewModel::class.java)
        mDevicesViewModel!!.setDataSource(mDatabase)
        mDevicesViewModel!!.refreshSource()
        mDevicesViewModel.getDataEvent().observe(this) { event: Bundle? ->
            if (event != null) {
                val record: Record =
                    event.getParcelable(DeviceOrDriverViewModel.Companion.ARG_DATAEVENT_RECORD)
                val action =
                    ModelDataAction.valueOf(event.getString(DeviceOrDriverViewModel.Companion.ARG_DATAEVENT_ACTION)!!)
                when (action) {
                    ModelDataAction.ADD, ModelDataAction.EDIT -> addOrEditDevice(
                        record as Device,
                        action
                    )
                    ModelDataAction.CONNECT -> connectToDevice(record as Device)
                    ModelDataAction.DELETE -> mDatabase.removeDevice(record as Device)
                    else -> {}
                }
            }
        }

        //setContentView(R.layout.activity_main);
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding!!.root)
        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager)
        val viewPager = mBinding!!.viewPager
        viewPager.adapter = sectionsPagerAdapter
        val tabs = mBinding!!.tabs
        tabs.setupWithViewPager(viewPager)
        if (!hasPermissions()) {
            requestPermissions()
        } else {
            mHasPermissions = true
        }
        mEfabAdd = findViewById(R.id.efab_add)
        mFabAddDevice = findViewById(R.id.fab_addDevice)
        mTextAddDevice = findViewById(R.id.text_addDevice)
        mFabAddDriver = findViewById(R.id.fab_addDriver)
        mTextAddDriver = findViewById(R.id.text_addDriver)
        mEfabAdd.setOnClickListener(View.OnClickListener { toggleAddFab() })
        mFabAddDriver.setOnClickListener(View.OnClickListener {
            toggleAddFab()
            addDriver()
        })
        mFabAddDevice.setOnClickListener(View.OnClickListener {
            toggleAddFab()
            addDevice()
        })
        toggleAddFab(true)
        mDeviceServiceManager = DeviceDriverServiceManager()

        /*Intent connectIntent = new Intent(this, BluetoothDeviceDriverService.class);
        bindService(connectIntent, mConnection, 0);*/
    }

    // TODO: Reorg
    private fun createAndBindService(deviceId: Int, cls: Class<*>?): IDeviceDriverService? {
        mDeviceServiceManager!!.createService(deviceId, this)
        ServiceUtils.startService(cls, applicationContext)
        val connectIntent = Intent(this, cls)
        bindService(connectIntent, mDeviceServiceManager!!.getConnection(deviceId)!!, 0)
        return mDeviceServiceManager!!.getService(deviceId)
    }

    private fun unbindAllServices() {
        val services = mDeviceServiceManager.getAllServices()
        for ((_, holder) in services!!) {
            if (holder != null && holder.service != null) {
                holder.service.unregisterStatusListener(this)
                unbindService(holder.connection)
            }
        }
    }

    private fun reregisterAllListeners() {
        val services = mDeviceServiceManager.getAllServices()
        for ((key) in services!!) {
            mDeviceServiceManager!!.recreateService(key!!, this)
            /*DeviceDriverServiceHolder holder = service.getValue();

            if (holder != null && holder.getService() != null) {
                holder.getService().registerStatusListener(this);
            }*/
        }
    }

    private fun unregisterAllListeners() {
        val services = mDeviceServiceManager.getAllServices()
        for ((_, holder) in services!!) {
            if (holder != null && holder.service != null) {
                holder.service.unregisterStatusListener(this)
            }
        }
    }

    private fun getService(deviceId: Int): IDeviceDriverService? {
        return mDeviceServiceManager!!.getService(deviceId)
    }

    private fun getConnection(deviceId: Int): ServiceConnection? {
        return mDeviceServiceManager!!.getConnection(deviceId)
    }

    public override fun onDestroy() {
        Log.v(TAG, "onDestroy")

        /*if (mService != null)
            mService.unregisterStatusListener(this);

        unbindService(mConnection);*/

        // TODO: Migrate
        unbindAllServices()
        super.onDestroy()
    }

    override fun onResume() {
        Log.v(TAG, "onResume")
        super.onResume()

        /*if (mService != null)
            mService.registerStatusListener(this);*/
        /*
        Intent connectIntent = new Intent(this, BluetoothDeviceDriverService.class);
        bindService(connectIntent, mConnection, 0);
        */

        // TODO: Migrate
        reregisterAllListeners()
    }

    override fun onPause() {
        Log.v(TAG, "onPause")
        super.onPause()

        /*if (mService != null)
            mService.unregisterStatusListener(this);

        //unbindService(mConnection);*/

        // TODO: Migrate
        unregisterAllListeners()
    }

    private fun toggleAddFab(forceHide: Boolean = false) {
        if (forceHide || mEfabAdd!!.isExtended) {
            mEfabAdd!!.shrink()
            mFabAddDevice!!.hide()
            mTextAddDevice!!.visibility = View.GONE
            mFabAddDriver!!.hide()
            mTextAddDriver!!.visibility = View.GONE
        } else {
            mEfabAdd!!.extend()
            mFabAddDriver!!.show()
            mTextAddDriver!!.visibility = View.VISIBLE
            mFabAddDevice!!.show()
            mTextAddDevice!!.visibility = View.VISIBLE
        }
    }

    private fun requestPermissions() {
        mPermissionsRequest.launch(PERMISSIONS)
    }

    private fun hasPermissions(): Boolean {
        for (permission in PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Permission not granted: $permission")
                return false
            }
            Log.d(TAG, "Permission granted: $permission")
        }
        Log.d(TAG, "All permissions granted as requested")
        return true
    }

    private fun addDriver() {
        addOrEditDriver(null, ModelDataAction.ADD)
    }

    private fun addOrEditDriver(driver: Driver?, action: ModelDataAction) {
        val dialog: DialogFragment =
            DriverEditFragment.Companion.createDialog(this@MainActivity, driver, action)
        dialog.show(supportFragmentManager, DriverEditFragment.Companion.TAG)
    }

    override fun onDriverEdit(action: ModelDataAction, driver: Driver) {
        Log.v(TAG, "onDriverEdit " + action + " on Driver: " + driver.toStringFull())
        mDatabase!!.addOrUpdateDriver(driver)
        mDriversViewModel!!.refreshSource()
        mDevicesViewModel!!.refreshSource()
    }

    private fun addDevice() {
        addOrEditDevice(null, ModelDataAction.ADD)
    }

    private fun addOrEditDevice(device: Device?, action: ModelDataAction) {
        val dialog: DialogFragment =
            DeviceEditFragment.Companion.createDialog(this@MainActivity, device, action)
        dialog.show(supportFragmentManager, DeviceEditFragment.Companion.TAG)
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
    private fun connectToDevice(device: Device?) {
        if (device != null) {
            var driver: Driver? = null
            var pttDriver: PttDriver? = null
            val service = mDeviceServiceManager!!.getService(device.id)
            if (device.driverId != -1) {
                driver = mDatabase.getDriver(device.driverId)
            } else {
                val drivers = mDatabase.getDrivers()
                for (test in drivers!!) {
                    if (test.deviceNameMatch != null) {
                        if (device.name.contains(test.deviceNameMatch)) {
                            driver = test
                        }
                    }
                }
            }
            pttDriver = if (driver != null) {
                try {
                    PttDriver(applicationContext, driver.json)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.d(TAG, "Received exception opening \"" + driver.name + "\": " + e)
                    Log.v(TAG, "Driver JSON: " + driver.json)

                    // Show error
                    return
                }
            } else {
                // Show error
                return
            }
            if (service == null ||
                service.javaClass != DeviceDriverServiceManager.Companion.getServiceClassForDeviceType(
                    device.deviceType,
                    pttDriver.type
                )
            ) {
                if (service != null && service.connectionState != DeviceConnectionState.Disconnected) {
                    service.disconnect()
                }
                createAndBindService(
                    device.id,
                    DeviceDriverServiceManager.Companion.getServiceClassForDeviceType(
                        device.deviceType,
                        pttDriver.type
                    )
                )

                // Re-run with a delay to give the service time to connect
                val handler = Handler(mainLooper)
                handler.postDelayed({ connectToDevice(device) }, 500)
                return
            }
            if (service != null) {
                if (service.connectionState != DeviceConnectionState.Disconnected) {
                    service.disconnect()
                }
                service.setPttDevice(device)
                if (service.deviceIsValid()) {
                    service.setPttDriver(null)
                    service.automaticallyReconnect = device.autoReconnect
                    service.setPttDriver(pttDriver)
                    service.connect()
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

    override fun onDeviceEdit(action: ModelDataAction, device: Device) {
        Log.v(TAG, "onDeviceEdit " + action + " on Device: " + device.toStringFull())
        mDatabase!!.addOrUpdateDevice(device)
        mDevicesViewModel!!.refreshSource()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mMenu = menu
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        mMenuConnect = mMenu!!.findItem(R.id.menu_action_bt_connect)
        mMenuDisconnect = mMenu!!.findItem(R.id.menu_action_bt_disconnect)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.menu_action_about_app -> showAboutDialog()
            R.id.menu_action_bt_settings -> {
                val intent = Intent()
                intent.action = Settings.ACTION_BLUETOOTH_SETTINGS
                startActivity(intent)
            }
            R.id.menu_action_bt_connect -> {}
            R.id.menu_action_bt_disconnect -> {}
            else -> {}
        }
        return true
    }

    override fun onStatusMessageUpdate(message: String?) {
        /*runOnUiThread(new Runnable() {
            public void run() {
                mStatusText.setText(message);
            }
        });*/
    }

    override fun onConnected() {
        Log.d(TAG, "onConnected")
        runOnUiThread {
            mMenuConnect!!.isVisible = false
            mMenuDisconnect!!.isVisible = true
        }
    }

    override fun onDisconnected() {
        Log.d(TAG, "onDisconnected")
        runOnUiThread {
            mMenuConnect!!.isVisible = true
            mMenuDisconnect!!.isVisible = false
        }
    }

    override fun onBatteryEvent(level: Byte) {
        Log.v(TAG, "Battery: $level%")
    }

    private fun showAboutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(
                Html.fromHtml(
                    getString(
                        R.string.about_dialog_title,
                        getString(R.string.app_name),
                        BuildConfig.VERSION_NAME
                    )
                )
            )
            .setMessage(Html.fromHtml(getString(R.string.about_dialog_body)))
            .setPositiveButton(getString(R.string.ok)) { dialog, whichButton ->
                // Do nothing
            }
            .create()
        dialog.show()

        // make links clickable:
        (dialog.findViewById<View>(android.R.id.message) as TextView?)!!.movementMethod =
            LinkMovementMethod.getInstance()
    } /*
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

    companion object {
        private val TAG = MainActivity::class.java.name
    }
}