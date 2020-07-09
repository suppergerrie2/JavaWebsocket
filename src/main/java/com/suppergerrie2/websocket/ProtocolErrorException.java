package com.suppergerrie2.websocket;

import com.suppergerrie2.websocket.common.Constants;

/**
 * Thrown when a protocol error has occurred as defined in <a href="https://tools.ietf.org/html/rfc6455">RFC-6455</a>. <br/>
 * A detailed message should be supplied explaining the protocol error that occurred.
 */
public class ProtocolErrorException extends Exception {

    public final Constants.StatusCode statusCode;

    public ProtocolErrorException(String message) {
        this(message, Constants.StatusCode.PROTOCOL_ERROR);
    }

    public ProtocolErrorException(String message, Constants.StatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
