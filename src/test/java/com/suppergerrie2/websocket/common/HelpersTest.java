package com.suppergerrie2.websocket.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

class HelpersTest {

    private static Stream<Arguments> byteArrayProvider() {
        return Stream.of(
                //@formatter:off
                Arguments.of(new byte[]{(byte) 0x41, (byte) 0xE2, (byte) 0x89, (byte) 0xA2, (byte) 0xCE, (byte) 0x91, (byte) 0x2E}, true),
                Arguments.of(new byte[]{(byte) 0b1100_0001, (byte) 0b1000_0001, (byte) 0xE2, (byte) 0x89, (byte) 0xA2, (byte) 0xCE, (byte) 0x91, (byte) 0x2E}, false), // Character not encoded in shortest form

                Arguments.of(new byte[]{(byte) 0xED, (byte) 0x95, (byte) 0x9C, (byte) 0xEA, (byte) 0xB5, (byte) 0xAD, (byte) 0xEC, (byte) 0x96, (byte) 0xB4}, true),
                Arguments.of(new byte[]{(byte) 0xE6, (byte) 0x97, (byte) 0xA5, (byte) 0xE6, (byte) 0x9C, (byte) 0xAC, (byte) 0xE8, (byte) 0xAA, (byte) 0x9E}, true),
                Arguments.of(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, (byte) 0xF0, (byte) 0xA3, (byte) 0x8E, (byte) 0xB4}, true),
                Arguments.of(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, (byte) 0xF0, (byte) 0xA3, (byte) 0x8E}, false),

                Arguments.of(new byte[]{(byte) 0xce, (byte) 0xba, (byte) 0xe1, (byte) 0xbd, (byte) 0xb9, (byte) 0xcf, (byte) 0x83, (byte) 0xce, (byte) 0xbc, (byte) 0xce, (byte) 0xb5, (byte) 0xed, (byte) 0xa0, (byte) 0x80, (byte) 0x65, (byte) 0x64, (byte) 0x69, (byte) 0x74, (byte) 0x65, (byte) 0x64}, false),
                Arguments.of(new byte[]{(byte) 0xce, (byte) 0xba, (byte) 0xe1, (byte) 0xbd, (byte) 0xb9, (byte) 0xcf, (byte) 0x83, (byte) 0xce, (byte) 0xbc, (byte) 0xce, (byte) 0xb5}, true),
                Arguments.of(new byte[]{(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, false),
                Arguments.of(new byte[]{(byte) 0x65, (byte) 0x64, (byte) 0x69, (byte) 0x74, (byte) 0x65, (byte) 0x64}, true),
                Arguments.of(new byte[]{(byte) 0xfb, (byte) 0xbf, (byte) 0xbf, (byte) 0xbf}, false) // Invalid leading byte
                //@formatter:on
                );
    }

    private static Stream<Arguments> getSecWebsocketArgProvider() {
        return Stream.of(
            Arguments.of("dGhlIHNhbXBsZSBub25jZQ==", "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
        );
    }

    @ParameterizedTest
    @MethodSource("byteArrayProvider")
    void isValidUTF8(byte[] byteArray, boolean valid) {
        Assertions.assertEquals(valid, Helpers.isValidUTF8(byteArray, false));
    }

    @ParameterizedTest
    @MethodSource("getSecWebsocketArgProvider")
    void getSecWebsocket(String secWebsocketKey, String expectedResult) {
        Assertions.assertEquals(expectedResult, Helpers.getSecWebsocket(secWebsocketKey));
    }
}