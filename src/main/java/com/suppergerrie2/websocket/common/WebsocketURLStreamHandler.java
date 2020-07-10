package com.suppergerrie2.websocket.common;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class WebsocketURLStreamHandler extends URLStreamHandler {

    private final boolean isSecure;

    WebsocketURLStreamHandler(boolean isSecure) {
        this.isSecure = isSecure;
    }

    @Override
    protected URLConnection openConnection(URL url) {
        return new WebsocketURLConnection(url);
    }

    @Override
    protected int getDefaultPort() {
        return isSecure ? 443 : 80;
    }

    @Override
    protected String toExternalForm(URL u) {
        StringBuilder result = new StringBuilder();
        result.append(u.getProtocol());
        result.append("://");
        result.append(u.getAuthority());
        if (u.getPath() != null) {
            result.append(u.getPath());
        }
        if (u.getQuery() != null) {
            result.append('?');
            result.append(u.getQuery());
        }
        return result.toString();
    }
}
