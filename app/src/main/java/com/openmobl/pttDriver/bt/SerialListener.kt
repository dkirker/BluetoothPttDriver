package com.openmobl.pttDriver.bt

import java.util.*

interface SerialListener {
    fun onSerialConnect()
    fun onSerialConnect(service: UUID?, characteristic: UUID?)
    fun onSerialConnectError(e: Exception)
    fun onSerialDisconnect()
    fun onSerialRead(data: ByteArray?, service: UUID?, characteristic: UUID?)
    fun onSerialIoError(e: Exception)
    fun onBatteryEvent(level: Byte)
}