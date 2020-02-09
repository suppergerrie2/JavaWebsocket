package com.suppergerrie2.websocket.common;

import java.net.URL;
import java.net.URLConnection;

public class WebsocketURLConnection extends URLConnection {
    /**
     * Constructs a URL connection to the specified URL. A connection to
     * the object referenced by the URL is not created.
     *
     * @param url the specified URL.
     */
    WebsocketURLConnection(URL url) {
        super(url);
    }

    @Override
    public void connect() {
        System.out.println("connected");
    }
}
