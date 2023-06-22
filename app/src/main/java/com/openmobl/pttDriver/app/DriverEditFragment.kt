package com.openmobl.pttDriver.app

import android.app.Activity
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.openmobl.pttDriver.R
import com.openmobl.pttDriver.model.*
import com.openmobl.pttDriver.utils.UIUtils

class DriverEditFragment : DialogFragment() {
    interface DriverEditListener {
        fun onDriverEdit(action: ModelDataAction, driver: Driver)
    }

    private var mDriverButton: MaterialButton? = null
    private var mDriverLabel: TextView? = null
    private var mPttDriver: PttDriver? = null
    private var mUpdated = false
    private var mListener: DriverEditListener? = null
    private val mActivityResultLauncher = registerForActivityResult<Intent, ActivityResult>(
        StartActivityForResult(),
        object : ActivityResultCallback<ActivityResult?> {
            override fun onActivityResult(result: ActivityResult) {
                if (result != null && result.resultCode == Activity.RESULT_OK) {
                    val intent = result.data
                    driverSelected(intent)
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
        })
    private val driver: Driver?
        private get() = arguments!!.getParcelable(ARGUMENT_DRIVER)
    private val action: ModelDataAction
        private get() = ModelDataAction.values()[arguments!!.getInt(ARGUMENT_ACTION)]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        mListener = try {
            activity as DriverEditListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement DriverEditListener!")
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog?)!!.getButton(Dialog.BUTTON_POSITIVE)
            .setOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View) {
                    if (mUpdated && validate()) {
                        val driver = createDriver()
                        mListener!!.onDriverEdit(action, driver)
                        dismiss()
                    } else {
                        dismiss()
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
        val view = inflater.inflate(R.layout.edit_driver, null, false)
        mDriverLabel = view.findViewById<View>(R.id.editdriver_label_pttDriverName) as TextView
        mDriverButton = view.findViewById<View>(R.id.editdriver_button_pttDriver) as MaterialButton
        mDriverButton!!.setOnClickListener { selectDriver() }
        val old = driver
        if (old != null) {
            mDriverLabel.setText(old.name)
        }

        // Fixes issues with text colour on light themes with pre-honeycomb devices.
        //builder.setInverseBackgroundForced(true);
        builder.setTitle(if (action == ModelDataAction.EDIT) R.string.add_driver else R.string.edit_driver)
        builder.setView(view)
        return builder.create()
    }

    private fun validate(): Boolean {
        return mPttDriver != null && mPttDriver!!.isValid
    }

    private fun createDriver(): Driver {
        val old = driver
        var id = -1
        if (old != null) {
            id = old.id
        }
        return Driver(
            id, mPttDriver.getDriverName(),
            mPttDriver.getType().toHumanReadableString(), mPttDriver!!.toJsonString(),
            mPttDriver.getDeviceName(), mPttDriver.getWatchForDeviceName()
        )
    }

    private fun selectDriver() {
        Log.v(TAG, "selectDriver ACTION_GET_CONTENT method")
        val filePicker = Intent(Intent.ACTION_GET_CONTENT)
        filePicker.addCategory(Intent.CATEGORY_OPENABLE)
        filePicker.type = "*/*"
        mActivityResultLauncher.launch(
            Intent.createChooser(
                filePicker,
                getString(R.string.select_driver)
            )
        )
    }

    private fun driverSelected(resultData: Intent?) {
        if (resultData != null) {
            val driverPath = resultData.data
            try {
                val driver = PttDriver(context, driverPath)
                Log.d(TAG, "Parsed PttDriver:\n$driver")
                Log.v(TAG, "JSON: " + driver.toJsonString())
                if (driver.isValid) {
                    mDriverLabel.setText(driver.driverName)
                    mPttDriver = driver
                    mUpdated = true
                } else {
                    Log.d(TAG, "Failed to open driver file. Driver file invalid.")
                    UIUtils.showDriverValidationError(
                        requireContext(),
                        requireContext().getString(R.string.driver_select_failed),
                        driver.allValidationErrors
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to open driver file. Received exception: $e")
                e.printStackTrace()
                // alert
            }
        }
    }

    companion object {
        val TAG = DriverEditFragment::class.java.name
        private const val ARGUMENT_DRIVER = "driver"
        private const val ARGUMENT_ACTION = "action"
        fun createDialog(
            context: Context?,
            driver: Driver?,
            action: ModelDataAction
        ): DialogFragment {
            val args = Bundle()
            args.putParcelable(ARGUMENT_DRIVER, driver)
            args.putInt(ARGUMENT_ACTION, action.ordinal)
            return instantiate(
                context!!,
                DriverEditFragment::class.java.name,
                args
            ) as DialogFragment
        }
    }
}