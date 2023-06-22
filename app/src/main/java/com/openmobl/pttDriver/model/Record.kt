package com.openmobl.pttDriver.model

interface Record {
    enum class RecordType {
        DRIVER, DEVICE
    }

    val id: Int
    val name: String?
    val details: String
    val recordType: RecordType
}