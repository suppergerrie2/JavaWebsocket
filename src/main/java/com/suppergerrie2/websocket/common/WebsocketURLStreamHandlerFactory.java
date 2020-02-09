package com.suppergerrie2.websocket.common;

import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class WebsocketURLStreamHandlerFactory implements URLStreamHandlerFactory {

    private static boolean initialized = false;

    public static void initialize() {
        if (!initialized) {
            initialized = true;
            URL.setURLStreamHandlerFactory(new WebsocketURLStreamHandlerFactory());
        }
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {

        if (protocol.equals("ws") || protocol.equals("wss")) {
            return new WebsocketURLStreamHandler(protocol.equals("wss"));
        }

        return null;
    }
}
