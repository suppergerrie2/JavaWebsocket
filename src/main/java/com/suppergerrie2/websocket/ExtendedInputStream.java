package com.suppergerrie2.websocket;

import java.io.IOException;
import java.io.InputStream;

public class ExtendedInputStream extends InputStream {

    final InputStream base;

    public ExtendedInputStream(InputStream base) {
        this.base = base;
    }

    @Override
    public int read() throws IOException {
        int r = base.read();

        if (r == -1) throw new IOException("End of stream");

        return r;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int r = super.read(b);

        if(r != b.length) {
            throw new IOException("Could not read all data, possibly end of stream");
        }

        return r;
    }

    public byte readByte() throws IOException {
        return (byte) read();
    }

    public short readShort() throws IOException {
        byte b1 = readByte();
        byte b0 = readByte();
        return (short) ((b1 << 8) | (b0 & 0xff));
    }

    public long readLong() throws IOException {
        byte b7 = readByte();
        byte b6 = readByte();
        byte b5 = readByte();
        byte b4 = readByte();
        byte b3 = readByte();
        byte b2 = readByte();
        byte b1 = readByte();
        byte b0 = readByte();

        // @formatter:off
        return ((((long)b7       ) << 56) |
                (((long)b6 & 0xff) << 48) |
                (((long)b5 & 0xff) << 40) |
                (((long)b4 & 0xff) << 32) |
                (((long)b3 & 0xff) << 24) |
                (((long)b2 & 0xff) << 16) |
                (((long)b1 & 0xff) <<  8) |
                (((long)b0 & 0xff)      ));
        // @formatter:on
    }

    public String readLine() throws IOException {
        StringBuilder builder = new StringBuilder();

        char lastChar, curChar = 0;

        do {
            lastChar = curChar;
            curChar = readChar();

            builder.append(curChar);

        } while (lastChar != '\r' && curChar != '\n');

        return builder.toString();
    }

    private char readChar() throws IOException {
        return (char) read();
    }
}
