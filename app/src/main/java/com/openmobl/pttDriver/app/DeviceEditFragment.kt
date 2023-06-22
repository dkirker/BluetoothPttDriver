package com.openmobl.pttDriver.app

import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import com.openmobl.pttDriver.R
import com.openmobl.pttDriver.db.DriverDatabase
import com.openmobl.pttDriver.db.DriverSQLiteDatabase
import com.openmobl.pttDriver.model.*
import com.openmobl.pttDriver.model.Device.DeviceType
import java.util.*

class DeviceEditFragment : DialogFragment() {
    interface DeviceEditListener {
        fun onDeviceEdit(action: ModelDataAction, device: Device)
    }

    private lateinit var mDeviceTypeDropdown: AutoCompleteTextView
    private lateinit var mDeviceButton: MaterialButton
    private lateinit var mDeviceLabel: TextView
    private lateinit var mDeviceMac: TextView
    private lateinit var mDriverDropdown: AutoCompleteTextView
    private lateinit var mAutoConnect: MaterialCheckBox
    private lateinit var mAutoReconnect: MaterialCheckBox
    private lateinit var mPttDownKeyDelay: TextInputLayout
    private lateinit var mDeviceSelectLayout: FlexboxLayout
    private lateinit var mMacAddressLayout: LinearLayout
    private lateinit var mAutoReconnectLayout: LinearLayout
    private lateinit var mDeviceNameLabel: TextView
    private var mAdvancedMode = false
    private var mAdvancedModeCountdown = 3
    private lateinit var mDeviceNameInput: TextInputLayout
    private lateinit var mDeviceMacInput: TextInputLayout
    private var mDeviceSelected = false
    private lateinit var mPttDevice: BluetoothDevice
    private lateinit var mDevicePicker: BroadcastReceiver
    private lateinit var mDriver: Driver
    private lateinit var mDrivers: List<Driver?>
    private var mEditingDeviceType: DeviceType? = DeviceType.BLUETOOTH
    private lateinit var mDb: DriverDatabase
    private lateinit var mListener: DeviceEditListener
    private val device: Device?
        private get() = if (arguments != null) arguments!!.getParcelable(ARGUMENT_DEVICE) else null
    private val action: ModelDataAction
        private get() = ModelDataAction.values()[if (arguments != null) arguments!!.getInt(
            ARGUMENT_ACTION
        ) else 1]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDb = DriverSQLiteDatabase(activity)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        mListener = try {
            activity as DeviceEditListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement DriverEditListener!")
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog?)!!.getButton(Dialog.BUTTON_POSITIVE)
            .setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View) {
                    if (validate()) {
                        val device = createDevice()
                        mListener!!.onDeviceEdit(action, device)
                        dismiss()
                    } else {
                        // TODO: Display error
                    }
                }
            })
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val actionName: String
        actionName = when (action) {
            ModelDataAction.ADD -> getString(R.string.add)
            ModelDataAction.EDIT -> getString(R.string.update)
            else -> throw RuntimeException("Unknown action " + action)
        }
        builder.setPositiveButton(actionName, null)
        builder.setNegativeButton(android.R.string.cancel, null)
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.edit_device, null, false)
        mDevicePicker = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent != null) {
                    val action = intent.action
                    if (SELECT_DEVICE_SELECTED == action) {
                        deviceSelected(intent)
                    }
                }
                context.unregisterReceiver(mDevicePicker)
            }
        }
        mDeviceTypeDropdown = view.findViewById(R.id.editdevice_dropdown_pttDeviceTypeSelect)
        mDeviceNameLabel = view.findViewById(R.id.editdevice_label_pttDevice)
        mDeviceLabel = view.findViewById(R.id.editdevice_label_pttDeviceName)
        mDeviceButton = view.findViewById(R.id.editdevice_button_pttDevice)
        mDeviceMac = view.findViewById(R.id.editdevice_label_pttDeviceMac)
        mDriverDropdown = view.findViewById(R.id.editdevice_dropdown_pttDriverSelect)
        mAutoConnect = view.findViewById(R.id.editdevice_checkBox_autoConnect)
        mAutoReconnect = view.findViewById(R.id.editdevice_checkBox_autoReonnect)
        mPttDownKeyDelay = view.findViewById(R.id.editdevice_input_pttDevicePttDownKeyDelay)
        mDeviceSelectLayout = view.findViewById(R.id.editdevice_layout_pttSelectDevice)
        mMacAddressLayout = view.findViewById(R.id.editdevice_layout_pttMac)
        mAutoReconnectLayout = view.findViewById(R.id.editdevice_layout_pttAutoReconnect)
        mDeviceNameInput = view.findViewById(R.id.editdevice_input_pttDeviceName)
        mDeviceMacInput = view.findViewById(R.id.editdevice_input_pttDeviceMac)
        val deviceTypeDropdownAdapter = ArrayAdapter(
            requireContext(),
            R.layout.driver_list_item,
            resources.getStringArray(R.array.device_type_names)
        )
        mDeviceTypeDropdown.setAdapter(deviceTypeDropdownAdapter)
        mDeviceTypeDropdown.setOnItemClickListener(OnItemClickListener { parent, view, position, id ->
            val types = resources.getStringArray(R.array.device_type_values)
            mEditingDeviceType = DeviceType.Companion.toDeviceType(types[position])
            updateLayouts()
        })
        mDeviceNameLabel.setOnClickListener(View.OnClickListener {
            if (mAdvancedModeCountdown == 0 && !mAdvancedMode) {
                mAdvancedMode = true
                if (mDeviceNameInput.getEditText() != null) {
                    mDeviceNameInput.getEditText()!!.setText(mDeviceLabel.getText())
                }
                mDeviceNameInput.setVisibility(View.VISIBLE)
                mDeviceLabel.setVisibility(View.GONE)
                if (mDeviceMacInput.getEditText() != null) {
                    mDeviceMacInput.getEditText()!!.setText(mDeviceMac.getText())
                }
                mDeviceMacInput.setVisibility(View.VISIBLE)
                mDeviceMac.setVisibility(View.GONE)
            } else {
                mAdvancedModeCountdown--
            }
        })
        mDeviceButton.setOnClickListener(View.OnClickListener { selectDevice() })
        mDrivers = allDrivers
        if (mDrivers != null && mDrivers.size > 0) {
            val driverDropdownAdapter =
                ArrayAdapter(requireContext(), R.layout.driver_list_item, mDrivers!!)
            mDriverDropdown.setAdapter(driverDropdownAdapter)
            mDriverDropdown.setOnItemClickListener(OnItemClickListener { parent, view, position, id ->
                mDriver = mDrivers[position]!!
                try {
                    val driverObj = PttDriver(requireContext(), mDriver.getJson())
                    if (mPttDownKeyDelay.getEditText() != null) {
                        mPttDownKeyDelay.getEditText()!!
                            .setText(Integer.toString(driverObj.readObj.defaultPttDownKeyDelay))
                    }
                    if (mEditingDeviceType == DeviceType.LOCAL) {
                        val generated =
                            resources.getString(R.string.local_device_address_prefix) + mDriver.getName()
                                .replace("[^a-zA-Z\\d]".toRegex(), "_")
                        mDeviceMac.setText(generated)
                        if (mAdvancedMode) {
                            if (mDeviceMacInput.getEditText() != null) {
                                mDeviceMacInput.getEditText()!!.setText(generated)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Driver JSON: " + mDriver.getJson())
                    e.printStackTrace()
                }
            })
        } else {
            val error = view.findViewById<View>(R.id.editdevice_label_pttNoDriversLabel) as TextView
            error.visibility = View.VISIBLE
            //mDriverDropdown.setInputType();
            mDriver = Driver.Companion.getEmptyDriver()
        }
        val old = device
        if (old != null) {
            mEditingDeviceType = old.deviceType
            mDeviceLabel.setText(old.name)
            mDeviceMac.setText(old.macAddress)
            mDeviceSelected = true
            mDriver = lookupDriver(old.driverId)
            mAutoConnect.setChecked(old.autoConnect)
            mAutoReconnect.setChecked(old.autoReconnect)
            if (mPttDownKeyDelay.getEditText() != null) mPttDownKeyDelay.getEditText()!!.setText(
                Integer.toString(old.pttDownDelay)
            )
            if (mDriver.getId() != -1) {
                mDriverDropdown.setText(mDriver.getName(), false)
            }
        } else {
            mAutoConnect.setChecked(Device.Companion.getAutoConnectDefault())
            mAutoReconnect.setChecked(Device.Companion.getAutoReconnectDefault())
            if (mPttDownKeyDelay.getEditText() != null) mPttDownKeyDelay.getEditText()!!.setText(
                Integer.toString(Device.Companion.getPttDownDelayDefault())
            )
        }
        mDeviceTypeDropdown.setText(getDeviceTypeNameFromValue(mEditingDeviceType), false)
        updateLayouts()

        // Fixes issues with text colour on light themes with pre-honeycomb devices.
        //builder.setInverseBackgroundForced(true);
        builder.setTitle(if (action == ModelDataAction.EDIT) R.string.edit_device else R.string.add_device)
        builder.setView(view)
        return builder.create()
    }

    private fun getDeviceTypeNameFromValue(type: DeviceType?): String {
        var result = type.toString()
        val names = resources.getStringArray(R.array.device_type_names)
        val values = resources.getStringArray(R.array.device_type_values)
        val pos = Arrays.binarySearch(values, result)
        if (pos >= 0) {
            result = names[pos]
        }
        return result
    }

    private fun updateLayouts() {
        when (mEditingDeviceType) {
            DeviceType.BLUETOOTH -> {
                if (mDeviceSelected) {
                    var deviceName: String? = ""
                    deviceName = if (mPttDevice != null) {
                        mPttDevice!!.name
                    } else {
                        val old = device
                        if (old != null) old.name else ""
                    }
                    mDeviceLabel!!.text = deviceName
                    if (mAdvancedMode) {
                        if (mDeviceNameInput!!.editText != null) {
                            mDeviceNameInput!!.editText!!.setText(deviceName)
                        }
                    }
                } else if (action == ModelDataAction.ADD) {
                    mDeviceLabel!!.setText(R.string.no_device)
                    if (mAdvancedMode) {
                        if (mDeviceNameInput!!.editText != null) {
                            mDeviceNameInput!!.editText!!.setText(R.string.no_device)
                        }
                    }
                }
                mDeviceSelectLayout!!.visibility = View.VISIBLE
                mMacAddressLayout!!.visibility = View.VISIBLE
                mAutoReconnectLayout!!.visibility = View.VISIBLE
            }
            DeviceType.LOCAL -> {
                if (action == ModelDataAction.ADD || mDeviceSelected) {
                    mDeviceLabel!!.setText(R.string.this_device)
                    if (mAdvancedMode) {
                        if (mDeviceNameInput!!.editText != null) {
                            mDeviceNameInput!!.editText!!.setText(R.string.this_device)
                        }
                    }
                }
                mDeviceSelectLayout!!.visibility = View.GONE
                mMacAddressLayout!!.visibility = View.GONE
                mAutoReconnectLayout!!.visibility = View.GONE
            }
        }
    }

    private fun lookupDriver(id: Int): Driver {
        return if (id >= 0) {
            val driver = mDb!!.getDriver(id)
            driver ?: Driver.Companion.getEmptyDriver()
        } else {
            Driver.Companion.getEmptyDriver()
        }
    }

    private val allDrivers: List<Driver>?
        private get() = mDb.getDrivers()

    private fun validate(): Boolean {
        // We assume that mDeviceMac has a value because that gets filled with mDeviceLabl.
        // The mDeviceLabel gets populated either from prior device or selecting a new device.
        var deviceValid = mEditingDeviceType == DeviceType.LOCAL || mDeviceSelected
        val driverValid = mDriver != null
        val inputValid =
            mPttDownKeyDelay!!.editText != null && mPttDownKeyDelay!!.editText!!.text != null &&
                    !mPttDownKeyDelay!!.editText!!.text.toString().isEmpty()
        if (mAdvancedMode) {
            if (mDeviceNameInput!!.editText != null && mDeviceMacInput!!.editText != null) {
                val mac = mDeviceMacInput!!.editText!!.text.toString()
                deviceValid = !mDeviceNameInput!!.editText!!.text.toString()
                    .isEmpty() && !mac.isEmpty() && BluetoothAdapter.checkBluetoothAddress(mac)
            }
        }
        return deviceValid && driverValid && inputValid
    }

    private fun createDevice(): Device {
        val old = device
        var id = -1
        var name: String? = ""
        var mac: String? = ""
        var driverId = -1
        var driverName: String? = ""
        val autoConnect = mAutoConnect!!.isChecked
        val autoReconnect = mAutoReconnect!!.isChecked
        var pttDownKeyDelay = 0
        if (mPttDownKeyDelay!!.editText != null) {
            pttDownKeyDelay = mPttDownKeyDelay!!.editText!!.text.toString().toInt()
        }
        if (old != null) {
            id = old.id
            name = old.name
            mac = old.macAddress
        }
        if (mDriver != null) {
            driverId = mDriver.getId()
            driverName = mDriver.getName()
        }
        when (mEditingDeviceType) {
            DeviceType.BLUETOOTH -> if (mPttDevice != null) {
                name = mPttDevice!!.name
                mac = mPttDevice!!.address
            }
            DeviceType.LOCAL -> {
                name = mDeviceLabel!!.text.toString()
                mac = mDeviceMac!!.text.toString()
            }
        }
        if (mAdvancedMode) {
            if (mDeviceNameInput!!.editText != null) {
                name = mDeviceNameInput!!.editText!!.text.toString()
            }
            if (mDeviceMacInput!!.editText != null) {
                mac = mDeviceMacInput!!.editText!!.text.toString()
            }
        }
        return Device(
            id, mEditingDeviceType, name, mac, driverId, driverName,
            autoConnect, autoReconnect, pttDownKeyDelay
        )
    }

    private fun selectDevice() {
        val filter = IntentFilter()
        filter.addAction(SELECT_DEVICE_SELECTED)
        requireContext().registerReceiver(mDevicePicker, filter)
        val intent = Intent(SELECT_DEVICE_ACTION)
        intent.flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        startActivity(intent)
        //mActivityResultLauncher.launch(intent);
    }

    private fun deviceSelected(resultData: Intent?) {
        if (resultData != null) {
            mPttDevice = resultData.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (mPttDevice != null) {
                Log.d(TAG, "Selected device: " + mPttDevice!!.name)
                mDeviceLabel!!.text = mPttDevice!!.name
                mDeviceMac!!.text = mPttDevice!!.address
                if (mAdvancedMode) {
                    if (mDeviceNameInput!!.editText != null) {
                        mDeviceNameInput!!.editText!!.setText(mPttDevice!!.name)
                    }
                    if (mDeviceMacInput!!.editText != null) {
                        mDeviceMacInput!!.editText!!.setText(mPttDevice!!.address)
                    }
                }
                mDeviceSelected = true
            }
        }
    }

    companion object {
        val TAG = DeviceEditFragment::class.java.name
        private const val ARGUMENT_DEVICE = "device"
        private const val ARGUMENT_ACTION = "action"
        private const val SELECT_DEVICE_ACTION = "android.bluetooth.devicepicker.action.LAUNCH"
        private const val SELECT_DEVICE_SELECTED =
            "android.bluetooth.devicepicker.action.DEVICE_SELECTED"

        fun createDialog(
            context: Context?,
            device: Device?,
            action: ModelDataAction
        ): DialogFragment {
            val args = Bundle()
            args.putParcelable(ARGUMENT_DEVICE, device)
            args.putInt(ARGUMENT_ACTION, action.ordinal)
            return instantiate(
                context!!,
                DeviceEditFragment::class.java.name,
                args
            ) as DialogFragment
        }
    }
}