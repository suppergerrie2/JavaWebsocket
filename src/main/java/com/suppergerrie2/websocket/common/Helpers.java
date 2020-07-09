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

    /**
     * Check if the byte array is valid UTF-8 according to <a href=https://tools.ietf.org/html/rfc3629>RFC-3629</a>.
     * The last character can be ignored in case this is only part of the complete data stream.
     *
     * Checks for:
     *    - Valid leading bytes
     *    - Shortest possible character encoding
     *    - Invalid surrogate values
     *    - Invalid codepoint values
     *    - Invalid "continuation" bytes
     *
     * @param byteArray The array of bytes to validate
     * @param ignoreIncompleteLastCharacter Whether to ignore if the last character is incomplete. An invalid leading byte or invalid size will still cause the array to be invalid.
     * @return true if the array is valid UTF-8. Else false
     */
    public static boolean isValidUTF8(byte[] byteArray, boolean ignoreIncompleteLastCharacter){

        int i = 0;
        while(i < byteArray.length) {
            int character = 0, charLength;

            byte leadingByte = byteArray[i];
            if((leadingByte & 0b1111_1000) == 0b1111_0000) {
                charLength = 4;
                character |= (leadingByte & 0b0000_0111) << 18;
            } else if ((leadingByte & 0b1111_0000) == 0b1110_0000) {
                charLength = 3;
                character |= (leadingByte & 0b0000_1111) << 12;
            } else if ((leadingByte & 0b1110_0000) == 0b1100_0000) {
                charLength = 2;
                character |= (leadingByte & 0b0001_1111) << 6;
            } else if((leadingByte & 0b1000_0000) == 0b0000_0000) {
                charLength = 1;
                character |= (leadingByte & 0b0111_1111);
            } else {
                return false;
            }

            for(int j = 1; j < charLength; j++) {
                if(i + j >= byteArray.length) return ignoreIncompleteLastCharacter;

                if((byteArray[i+j] & 0b1000_0000) != 0b1000_0000) {
                    return false;
                }

                character |= (byteArray[i+j] & 0b0011_1111) << ((charLength - j - 1) * 6);
            }

            //Surrogate pairs are not allowed
            if(character >= 0xD800 && character <= 0xDFFF) return false;

            //Characters should be encoded in the least amount of bytes.
            if(character <= 0x0000_007f && charLength != 1) return false;
            if(character >= 0x0000_0080 && character <= 0x0000_07FF && charLength != 2) return false;
            if(character >= 0x0000_0800 && character <= 0x0000_FFFF && charLength != 3) return false;
            if(character >= 0x0001_0000 && character <= 0x0010_FFFF && charLength != 4) return false;
            if(character > 0x10_FFFF) return false;

            i+=charLength;
        }

        return true;
    }

}
