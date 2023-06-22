package com.openmobl.pttDriver.app.ui.main

import android.content.*
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.openmobl.pttDriver.R
import java.lang.reflect.Array

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class SectionsPagerAdapter(private val mContext: Context, fm: FragmentManager?) :
    FragmentPagerAdapter(
        fm!!
    ) {
    override fun getItem(position: Int): Fragment {
        return DeviceOrDriverListFragment.Companion.newInstance(TAB_MAP[position])
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return mContext.resources.getString(TAB_TITLES[position])
    }

    override fun getCount(): Int {
        return Array.getLength(TAB_TITLES)
    }

    companion object {
        @StringRes
        private val TAB_TITLES = intArrayOf(
            R.string.devices,
            R.string.drivers
        )
        private val TAB_MAP = arrayOf(
            DeviceOrDriverListFragment.DataSource.DEVICES,
            DeviceOrDriverListFragment.DataSource.DRIVERS
        )
    }
}