package com.suppergerrie2.websocket;

/**
 * Thrown when a protocol error has occurred as defined in <a href="https://tools.ietf.org/html/rfc6455">RFC-6455</a>. <br/>
 * A detailed message should be supplied explaining the protocol error that occurred.
 */
public class ProtocolErrorException extends Exception {
    public ProtocolErrorException(String message) {
        super(message);
    }
}
