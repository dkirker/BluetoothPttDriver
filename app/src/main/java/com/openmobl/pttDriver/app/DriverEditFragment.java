package com.openmobl.pttDriver.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.openmobl.pttDriver.model.Driver;
import com.openmobl.pttDriver.model.ModelDataAction;
import com.openmobl.pttDriver.R;
import com.openmobl.pttDriver.model.PttDriver;
import com.openmobl.pttDriver.utils.UIUtils;

public class DriverEditFragment extends DialogFragment {
    public static final String TAG = DriverEditFragment.class.getName();

    private static final String ARGUMENT_DRIVER = "driver";
    private static final String ARGUMENT_ACTION = "action";

    public interface DriverEditListener {
        void onDriverEdit(ModelDataAction action, Driver driver);
    }

    private MaterialButton mDriverButton;
    private TextView mDriverLabel;

    private PttDriver mPttDriver;
    private boolean mUpdated = false;

    private DriverEditListener mListener;
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

    public static DialogFragment createDialog(Context context, Driver driver, ModelDataAction action) {
        Bundle args = new Bundle();

        args.putParcelable(ARGUMENT_DRIVER, driver);
        args.putInt(ARGUMENT_ACTION, action.ordinal());

        return (DialogFragment)Fragment.instantiate(context, DriverEditFragment.class.getName(), args);
    }

    private Driver getDriver() {
        return getArguments().getParcelable(ARGUMENT_DRIVER);
    }

    private ModelDataAction getAction() {
        return ModelDataAction.values()[getArguments().getInt(ARGUMENT_ACTION)];
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mListener = (DriverEditListener)activity;
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
                if (mUpdated && validate()) {
                    Driver driver = createDriver();
                    mListener.onDriverEdit(getAction(), driver);
                    dismiss();
                } else {
                    dismiss();
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
        View view = inflater.inflate(R.layout.edit_driver, null, false);

        mDriverLabel = (TextView)view.findViewById(R.id.editdriver_label_pttDriverName);
        mDriverButton = (MaterialButton)view.findViewById(R.id.editdriver_button_pttDriver);

        mDriverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectDriver();
            }
        });

        Driver old = getDriver();
        if (old != null) {
            mDriverLabel.setText(old.getName());
        }

        // Fixes issues with text colour on light themes with pre-honeycomb devices.
        //builder.setInverseBackgroundForced(true);

        builder.setTitle(getAction() == ModelDataAction.EDIT ? R.string.add_driver : R.string.edit_driver);

        builder.setView(view);

        return builder.create();
    }

    private boolean validate() {
        return mPttDriver != null && mPttDriver.isValid();
    }

    private Driver createDriver() {
        Driver old = getDriver();
        int id = -1;

        if (old != null) {
            id = old.getId();
        }

        return new Driver(id, mPttDriver.getDriverName(),
                        mPttDriver.getType().toHumanReadableString(), mPttDriver.toJsonString(),
                        mPttDriver.getDeviceName(), mPttDriver.getWatchForDeviceName());
    }

    private void selectDriver() {
        Log.v(TAG, "selectDriver ACTION_GET_CONTENT method");

        Intent filePicker = new Intent(Intent.ACTION_GET_CONTENT);
        filePicker.addCategory(Intent.CATEGORY_OPENABLE);
        filePicker.setType("*/*");
        mActivityResultLauncher.launch(Intent.createChooser(filePicker,
                getString(R.string.select_driver)));
    }
    private void driverSelected(Intent resultData) {
        if (resultData != null) {
            Uri driverPath = resultData.getData();

            try {
                PttDriver driver = new PttDriver(getContext(), driverPath);

                Log.d(TAG, "Parsed PttDriver:\n" + driver);

                Log.v(TAG, "JSON: " + driver.toJsonString());

                if (driver.isValid()) {
                    mDriverLabel.setText(driver.getDriverName());

                    mPttDriver = driver;

                    mUpdated = true;
                } else {
                    Log.d(TAG, "Failed to open driver file. Driver file invalid.");

                    UIUtils.showDriverValidationError(requireContext(),
                            requireContext().getString(R.string.driver_select_failed),
                            driver.getAllValidationErrors());
                }
            } catch (Exception e) {
                Log.d(TAG, "Failed to open driver file. Received exception: " + e);
                e.printStackTrace();
                // alert
            }
        }
    }
}
