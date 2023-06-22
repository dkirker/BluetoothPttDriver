package com.openmobl.pttDriver.app.ui.main

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.ListFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.openmobl.pttDriver.R
import com.openmobl.pttDriver.databinding.FragmentMainBinding
import com.openmobl.pttDriver.model.Device
import com.openmobl.pttDriver.model.Driver
import com.openmobl.pttDriver.model.ModelDataAction
import com.openmobl.pttDriver.model.Record
import com.openmobl.pttDriver.model.Record.RecordType

/**
 * A placeholder fragment containing a simple view.
 */
class DeviceOrDriverListFragment : ListFragment() {
    //public static final String DATA_SOURCE_DRIVERS = "drivers";
    //public static final String DATA_SOURCE_DEVICES = "devices";
    enum class DataSource {
        DRIVERS, DEVICES
    }

    private var mViewModel: DeviceOrDriverViewModel? = null
    private var mBinding: FragmentMainBinding? = null
    private var mRecords: List<Record>? = null
    private var mListAdapter: ArrayAdapter<Record>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        super.onCreate(savedInstanceState)
        var dataSource = DataSource.DEVICES
        if (arguments != null) {
            dataSource = DataSource.valueOf(
                arguments!!.getString(ARG_DATA_SOURCE)!!
            )
        }
        Log.v(TAG, "onCreate for $dataSource")
        mViewModel = when (dataSource) {
            DataSource.DRIVERS -> ViewModelProvider(requireActivity()).get(
                DriversViewModel::class.java
            )
            DataSource.DEVICES -> ViewModelProvider(requireActivity()).get(
                DevicesViewModel::class.java
            )
            else -> ViewModelProvider(requireActivity()).get(
                DevicesViewModel::class.java
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mBinding = FragmentMainBinding.inflate(inflater, container, false)
        val root: View = mBinding!!.root
        val pullToRefresh =
            root.findViewById<SwipeRefreshLayout>(R.id.devicesordrivers_swiperefresh)
        pullToRefresh?.setOnRefreshListener {
            mViewModel!!.refreshSource()
            pullToRefresh.isRefreshing = false
        }
        mViewModel.getRecords().observe(viewLifecycleOwner, object : Observer<List<Record?>?> {
            override fun onChanged(records: List<Record>?) {
                Log.v(TAG, "mViewModel has changed!")
                mRecords = records
                listAdapter = null
                mListAdapter = object : ArrayAdapter<Record?>(activity!!, 0, mRecords!!) {
                    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
                        var view = view
                        Log.v(TAG, "setting up mListAdapter")
                        val entry = mRecords!![position]
                        if (view == null) view = activity!!.layoutInflater.inflate(
                            R.layout.deviceordriver_list_item,
                            parent,
                            false
                        )
                        val text1 = view!!.findViewById<TextView>(R.id.deviceordriver_name)
                        val text2 = view.findViewById<TextView>(R.id.deviceordriver_type)
                        text1.text = entry.name
                        text2.text = entry.details
                        val more = view.findViewById<ImageView>(R.id.deviceordriver_more)
                        more?.setOnClickListener { onOptionsClick(entry, more) }
                        val btStatus =
                            view.findViewById<ImageView>(R.id.deviceordriver_status_disconnected)
                        if (btStatus != null && entry.recordType == RecordType.DEVICE) {
                            btStatus.visibility = View.VISIBLE
                        }
                        return view
                    }
                }
                listAdapter = mListAdapter
            }
        })
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mBinding = null
    }

    private fun callOnModelDataActionEvent(record: Record, action: ModelDataAction) {
        mViewModel!!.sendDataEvent(record, action)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        Log.v(TAG, "Item clicked: $position")
        val record = mRecords!![position]
        if (record is Device) {
            callOnModelDataActionEvent(record, ModelDataAction.CONNECT)
        } else if (record is Driver) {
            callOnModelDataActionEvent(record, ModelDataAction.EDIT)
        }
    }

    private fun onPopupItemClick(record: Record, menuItem: Int): Boolean {
        var action = ModelDataAction.NONE
        when (menuItem) {
            R.id.deviceordriver_moremenu_edit -> action = ModelDataAction.EDIT
            R.id.deviceordriver_moremenu_delete -> action = ModelDataAction.DELETE
        }
        Log.v(TAG, "More menu item clicked fpr Record: $record, MenuItem $menuItem -> $action")
        callOnModelDataActionEvent(record, action)
        return true
    }

    private fun onOptionsClick(record: Record, optionsButton: View) {
        val popupMenu = PopupMenu(requireContext(), optionsButton)
        popupMenu.inflate(R.menu.deviceordriver_more_menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            onPopupItemClick(
                record,
                menuItem.itemId
            )
        }
        popupMenu.show()
    }

    companion object {
        private val TAG = DeviceOrDriverListFragment::class.java.name
        private const val ARG_DATA_SOURCE = "data_source"
        fun newInstance(dataSource: DataSource): DeviceOrDriverListFragment {
            val fragment = DeviceOrDriverListFragment()
            val bundle = Bundle()
            Log.v(TAG, "newInstance of $dataSource")
            bundle.putString(ARG_DATA_SOURCE, dataSource.toString())
            fragment.arguments = bundle
            return fragment
        }
    }
}