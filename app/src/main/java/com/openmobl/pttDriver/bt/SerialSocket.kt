package com.openmobl.pttDriver.bt

import java.io.IOException

interface SerialSocket {
    val name: String?
    val address: String?
    fun disconnect()
    fun disconnect(silent: Boolean)

    @Throws(IOException::class)
    fun connect(listener: SerialListener?)

    @Throws(IOException::class)
    fun write(data: ByteArray)
}