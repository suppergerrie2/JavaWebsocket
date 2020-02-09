package com.suppergerrie2.websocket.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Helpers {

    /**
     * Generate the Sec-WebSocket-Accept value as defined in <a href="https://tools.ietf.org/html/rfc6455#section-4">RFC-6455 4.</a>.
     * <p>
     * It first concatenates the given key and {@link Constants#WEBSOCKET_KEY_GUID}, then creates the SHA-1 hash and encodes that using base64.
     *
     * @param secWebsocketKey The nonce the client sends.
     * @return The expected key.
     */
    public static String getSecWebsocket(String secWebsocketKey) {
        MessageDigest md;

        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }

        return Base64.getEncoder()
                     .encodeToString(md.digest((secWebsocketKey + Constants.WEBSOCKET_KEY_GUID.toString().toUpperCase())
                                                       .getBytes(StandardCharsets.UTF_8)));
    }

}
