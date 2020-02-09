package com.suppergerrie2.websocket.common;

import java.util.UUID;

@SuppressWarnings("WeakerAccess")
public class Constants {

    /**
     * The magic GUID used to check the NONCE as defined in <a href="https://tools.ietf.org/html/rfc6455">rfc-6455</a>
     */
    public static final UUID WEBSOCKET_KEY_GUID = UUID.fromString("258EAFA5-E914-47DA-95CA-C5AB0DC85B11");

}
