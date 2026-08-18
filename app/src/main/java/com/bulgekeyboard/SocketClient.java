package com.bulgekeyboard;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.OutputStream;

public class SocketClient {

    private OutputStream os;

    public SocketClient() {
        try {
            LocalSocket socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(
                    "/data/local/tmp/vmouse.sock",
                    LocalSocketAddress.Namespace.FILESYSTEM
            ));
            os = socket.getOutputStream();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(char c) {
        try {
            if (os != null) {
                os.write(c);
                os.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}