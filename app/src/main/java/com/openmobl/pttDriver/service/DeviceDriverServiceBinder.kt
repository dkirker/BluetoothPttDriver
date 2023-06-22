package com.openmobl.pttDriver.service

import android.os.Binder

class DeviceDriverServiceBinder(val service: IDeviceDriverService) : Binder()