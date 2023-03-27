package com.openmobl.pttDriver.service;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.openmobl.pttDriver.model.Device.DeviceType;
import com.openmobl.pttDriver.model.PttDriver.ConnectionType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DeviceDriverServiceManager {
    private static final String TAG = DeviceDriverServiceManager.class.getName();

    public static class DeviceDriverServiceHolder {
        private IDeviceDriverService mService;
        private ServiceConnection mConnection;

        public DeviceDriverServiceHolder(DeviceStatusListener listener) {
            mConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.v(TAG, "onServiceConnected");
                    mService = ((DeviceDriverServiceBinder)service).getService();

                    mService.registerStatusListener(listener);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.v(TAG, "onServiceDisconnected");
                    mService = null;
                }
            };
        }

        public IDeviceDriverService getService() {
            return mService;
        }

        public ServiceConnection getConnection() {
            return mConnection;
        }
    }

    private Map<Integer, DeviceDriverServiceHolder> mServiceMap;

    public DeviceDriverServiceManager() {
        mServiceMap = new HashMap<>();
    }

    public void createService(int deviceId, DeviceStatusListener listener) {
        mServiceMap.put(deviceId, new DeviceDriverServiceHolder(listener));
    }

    public void recreateService(int deviceId, DeviceStatusListener listener) {
        IDeviceDriverService oldService = getService(deviceId);

        if (oldService == null) {
            createService(deviceId, listener);
        }
    }

    public DeviceDriverServiceHolder getServiceHolder(int deviceId) {
        return mServiceMap.get(deviceId);
    }

    public Map<Integer, DeviceDriverServiceHolder> getAllServices() {
        return Collections.unmodifiableMap(mServiceMap);
    }

    public IDeviceDriverService getService(int deviceId) {
        DeviceDriverServiceHolder holder = getServiceHolder(deviceId);

        return (holder != null) ? holder.getService() : null;
    }

    public ServiceConnection getConnection(int deviceId) {
        DeviceDriverServiceHolder holder = getServiceHolder(deviceId);

        return (holder != null) ? holder.getConnection() : null;
    }

    public static Class<?> getServiceClassForDeviceType(DeviceType deviceType, ConnectionType driverConnection) {
        if (deviceType == DeviceType.BLUETOOTH) {
            return BluetoothDeviceDriverService.class;
        } else if (deviceType == DeviceType.LOCAL) {
            if (driverConnection == ConnectionType.FILESTREAM) {
                return FileStreamDeviceDriverService.class;
            }
        }
        return null; // ??
    }
}
