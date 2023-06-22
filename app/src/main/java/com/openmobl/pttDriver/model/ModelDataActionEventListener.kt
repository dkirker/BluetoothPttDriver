package com.openmobl.pttDriver.model

interface ModelDataActionEventListener {
    fun onModelDataActionEvent(record: Record?, action: ModelDataAction?)
}