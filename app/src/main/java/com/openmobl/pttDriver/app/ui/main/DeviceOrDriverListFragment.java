package com.openmobl.pttDriver.app.ui.main;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.ListFragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.openmobl.pttDriver.R;
import com.openmobl.pttDriver.databinding.FragmentMainBinding;
import com.openmobl.pttDriver.model.Device;
import com.openmobl.pttDriver.model.Driver;
import com.openmobl.pttDriver.model.ModelDataAction;
import com.openmobl.pttDriver.model.ModelDataActionEventListener;
import com.openmobl.pttDriver.model.Record;

import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class DeviceOrDriverListFragment extends ListFragment {
    private static final String TAG = DeviceOrDriverListFragment.class.getName();

    private static final String ARG_DATA_SOURCE = "data_source";

    //public static final String DATA_SOURCE_DRIVERS = "drivers";
    //public static final String DATA_SOURCE_DEVICES = "devices";

    enum DataSource { DRIVERS, DEVICES }

    private DeviceOrDriverViewModel mViewModel;
    private FragmentMainBinding mBinding;

    private List<Record> mRecords;
    private ArrayAdapter<Record> mListAdapter;

    public static DeviceOrDriverListFragment newInstance(DataSource dataSource) {
        DeviceOrDriverListFragment fragment = new DeviceOrDriverListFragment();
        Bundle bundle = new Bundle();

        Log.v(TAG, "newInstance of " + dataSource);

        bundle.putString(ARG_DATA_SOURCE, dataSource.toString());
        fragment.setArguments(bundle);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        super.onCreate(savedInstanceState);

        DataSource dataSource = DataSource.DEVICES;

        if (getArguments() != null) {
            dataSource = DataSource.valueOf(getArguments().getString(ARG_DATA_SOURCE));
        }

        Log.v(TAG, "onCreate for " + dataSource);

        switch (dataSource) {
            case DRIVERS:
                mViewModel = new ViewModelProvider(requireActivity()).get(DriversViewModel.class);
                break;
            case DEVICES:
            default:
                mViewModel = new ViewModelProvider(requireActivity()).get(DevicesViewModel.class);
                break;
        }
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        mBinding = FragmentMainBinding.inflate(inflater, container, false);
        View root = mBinding.getRoot();

        final SwipeRefreshLayout pullToRefresh = root.findViewById(R.id.devicesordrivers_swiperefresh);

        if (pullToRefresh != null) {
            pullToRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    mViewModel.refreshSource();
                    pullToRefresh.setRefreshing(false);
                }
            });
        }

        mViewModel.getRecords().observe(getViewLifecycleOwner(), new Observer<List<Record>>() {
            @Override
            public void onChanged(@Nullable List<Record> records) {
                Log.v(TAG, "mViewModel has changed!");

                mRecords = records;

                setListAdapter(null);

                mListAdapter = new ArrayAdapter<Record>(getActivity(), 0, mRecords) {
                    @NonNull
                    @Override
                    public View getView(int position, View view, @NonNull ViewGroup parent) {
                        Log.v(TAG, "setting up mListAdapter");
                        final Record entry = mRecords.get(position);

                        if (view == null)
                            view = getActivity().getLayoutInflater().inflate(R.layout.deviceordriver_list_item, parent, false);

                        TextView text1 = view.findViewById(R.id.deviceordriver_name);
                        TextView text2 = view.findViewById(R.id.deviceordriver_type);
                        text1.setText(entry.getName());
                        text2.setText(entry.getDetails());

                        final ImageView more = view.findViewById(R.id.deviceordriver_more);
                        if (more != null) {
                            more.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    onOptionsClick(entry, more);
                                }
                            });
                        }

                        final ImageView btStatus = view.findViewById(R.id.deviceordriver_status_disconnected);
                        if (btStatus != null && entry.getRecordType() == Record.RecordType.DEVICE) {
                            btStatus.setVisibility(View.VISIBLE);
                        }

                        return view;
                    }
                };

                setListAdapter(mListAdapter);
            }
        });
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    private void callOnModelDataActionEvent(Record record, ModelDataAction action) {
        mViewModel.sendDataEvent(record, action);
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        Log.v(TAG, "Item clicked: " + position);
        Record record = mRecords.get(position);

        if (record instanceof Device) {
            callOnModelDataActionEvent(record, ModelDataAction.CONNECT);
        } else if (record instanceof Driver) {
            callOnModelDataActionEvent(record, ModelDataAction.EDIT);
        }
    }

    private boolean onPopupItemClick(Record record, int menuItem) {
        ModelDataAction action = ModelDataAction.NONE;

        switch (menuItem) {
            case R.id.deviceordriver_moremenu_edit:
                action = ModelDataAction.EDIT;
                break;
            case R.id.deviceordriver_moremenu_delete:
                action = ModelDataAction.DELETE;
                break;
        }

        Log.v(TAG, "More menu item clicked fpr Record: " + record + ", MenuItem " + menuItem + " -> " + action);

        callOnModelDataActionEvent(record, action);

        return true;
    }

    private void onOptionsClick(final Record record, View optionsButton) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), optionsButton);
        popupMenu.inflate(R.menu.deviceordriver_more_menu);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                return onPopupItemClick(record, menuItem.getItemId());
            }
        });
        popupMenu.show();
    }

}