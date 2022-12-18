package com.openmobl.pttDriver.model;

public interface Record {
    enum RecordType { DRIVER, DEVICE }

    int getId();
    String getName();
    String getDetails();
    RecordType getRecordType();
}
