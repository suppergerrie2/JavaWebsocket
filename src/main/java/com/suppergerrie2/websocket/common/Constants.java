package com.suppergerrie2.websocket.common;

import java.util.UUID;

@SuppressWarnings("WeakerAccess")
public class Constants {

    /**
     * The magic GUID used to check the NONCE as defined in <a href="https://tools.ietf.org/html/rfc6455">rfc-6455</a>
     */
    public static final UUID WEBSOCKET_KEY_GUID = UUID.fromString("258EAFA5-E914-47DA-95CA-C5AB0DC85B11");

    public enum StatusCode {
        NO_ERROR(1000),
        GOING_AWAY(1001),
        PROTOCOL_ERROR(1002),
        UNSUPPORTED_DATA_TYPE(1003),
        RESERVED(1004),
        EXPECTS_STATUS_CODE(1005),
        EXPECTS_STATUS_CODE_ABNORMALLY(1006),
        INCONSISTENT_DATA_TYPE(1007),
        POLICY_VIOLATION(1008),
        MESSAGE_TOO_BIG(1009),
        EXPECTS_EXTENSION(1010),
        UNEXPECTED_EXCEPTION(1011),
        TLS_HANDSHAKE_FAILURE(1012);

        public final int value;

        StatusCode(int value) {
            this.value = value;
        }
    }

}
