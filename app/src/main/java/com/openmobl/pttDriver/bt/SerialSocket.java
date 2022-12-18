package com.openmobl.pttDriver.bt;


import java.io.IOException;

public interface SerialSocket {
    String getName();
    String getAddress();
    void disconnect();
    void disconnect(boolean silent);
    void connect(SerialListener listener)  throws IOException;
    void write(byte[] data) throws IOException;
}
