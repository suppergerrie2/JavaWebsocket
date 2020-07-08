package com.suppergerrie2.websocket.common;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.UUID;

@SuppressWarnings("WeakerAccess")
public class Constants {

    /**
     * The magic GUID used to check the NONCE as defined in <a href="https://tools.ietf.org/html/rfc6455">rfc-6455</a>
     */
    public static final UUID WEBSOCKET_KEY_GUID = UUID.fromString("258EAFA5-E914-47DA-95CA-C5AB0DC85B11");

    /**
     * StatusCodes as defined in <a href=https://tools.ietf.org/html/rfc6455#section-7.4.1>RFC-6455 Section 7.4.1.</a>
     */
    public enum StatusCode {
        INVALID_STATUS_CODE(-1),
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
        TLS_HANDSHAKE_FAILURE(1012),
        APPLICATION_RESERVED(3000), // 3000 - 3999
        PRIVATE_USE(4000) //4000 - 4999
        ;

        public final int value;
        private final static HashMap<Integer, StatusCode> idToStatusCode = new HashMap<>();

        static {
            EnumSet.allOf(StatusCode.class).forEach(statusCode -> idToStatusCode.put(statusCode.value, statusCode));
        }

        StatusCode(int value) {
            this.value = value;
        }

        /**
         * Get a status code for the given integer id.
         *
         * @param id The id to get the {@link StatusCode} for
         * @return If the id is in the range 3000 up to (and including) 3999 {@link StatusCode#APPLICATION_RESERVED} is returned. <br/>
         *         If the id is in the range 4000 up to (and including) 4999 {@link StatusCode#PRIVATE_USE} is returned. <br/>
         *
         *         If the id does not map to a reserved status code {@link StatusCode#INVALID_STATUS_CODE} is returned. <br/>
         *         Else the {@link StatusCode} mapping to the given id is returned as defined in <a href=https://tools.ietf.org/html/rfc6455#section-7.4.1>RFC-6455 Section 7.4.1.</a>
         */
        public static StatusCode fromInteger(int id) {
            if( id >= 3000 && id < 4000) return APPLICATION_RESERVED;
            if( id >= 4000 && id < 5000) return PRIVATE_USE;

            return idToStatusCode.getOrDefault(id, INVALID_STATUS_CODE);
        }
    }

}
