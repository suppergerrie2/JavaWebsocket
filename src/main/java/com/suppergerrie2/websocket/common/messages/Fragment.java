package com.suppergerrie2.websocket.common.messages;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class Fragment {

    //Max size of the payload in a fragment, if the amount of bytes being put in the fragment exceeds this the bytes will be split across fragments
    @SuppressWarnings("WeakerAccess")
    public static int MAX_FRAGMENT_PAYLOAD_SIZE = Integer.MAX_VALUE;

    //Random to generate a nonce
    private static SecureRandom random = new SecureRandom();
    final OpCode opCode;
    final byte[] payloadData;
    private final boolean rsv1;
    private final boolean rsv2;
    private final boolean rsv3;
    private final boolean hasMask;
    private final byte[] mask;
    public boolean fin;

    /**
     * Create a fragment from a buffer.
     * It will read from {@link ByteBuffer#position()} and parse the bytes according to <a href="https://tools.ietf.org/html/rfc6455#section-5.2">RFC-6455 section 5.2.</a>
     *
     * @param buffer The buffer containing the data to create a fragment from.
     */
    public Fragment(ByteBuffer buffer) {

        //Get the first byte
        byte b = buffer.get();

        //Check if the fin, rsv1, rsv2 and rsv3 bits are set and if so set the flag
        fin = (b & 0b10000000) > 0;
        rsv1 = (b & 0b01000000) > 0;
        rsv2 = (b & 0b00100000) > 0;
        rsv3 = (b & 0b00010000) > 0;

        //Read the opcode from the last 4 bits
        byte opCode = (byte) (b & 0b00001111);
        //And set the opcode
        this.opCode = OpCode.getOpcode(opCode);

        //Get the next byte, which contains the mask flag and the (first) payload length
        b = buffer.get();
        //Mask flag is the most significant bit
        hasMask = (b & 0b10000000) > 0;

        //The payload length is the last 7 bits
        long payloadLength = b & 0b01111111;

        //If the payloadLength is 126 the next 2 bytes contain a short with the payloadLength
        if (payloadLength == 126) {
            payloadLength = buffer.getShort() & 0xffff; //Only need the last 2 bytes
        } else if (payloadLength == 127) { //If the payloadLength is 127 the next 8 bytes contain a long with the payloadLength
            payloadLength = buffer.getLong();
        }

        //If there is a mask read the mask, else just set it to an empty array
        if (hasMask) {
            //mask is 4 bytes
            mask = new byte[4];
            buffer.get(mask);
        } else {
            mask = new byte[0];
        }

        //Give an error if we have more than Integer.MAX_VALUE bytes. This is 4GB so should not happen a lot.
        if (payloadLength > Integer.MAX_VALUE) {
            throw new UnsupportedOperationException("Cannot yet decode a message with this many bytes!");
        }

        //Prepare the array.
        payloadData = new byte[(int) payloadLength];
        //And load the data into it
        buffer.get(payloadData);

        //If there is a mask unmask the payload data
        if (hasMask) {
            for (int i = 0; i < payloadData.length; i++) {
                payloadData[i] = (byte) (payloadData[i] ^ mask[i % 4]);
            }
        }
    }

    private Fragment(OpCode opCode, byte[] bytes) {
        rsv1 = rsv2 = rsv3 = false;
        fin = false;
        hasMask = true;
        this.opCode = opCode;
        mask = new byte[4];
        random.nextBytes(mask);
        payloadData = bytes;
    }

    /**
     * Create a fragment with the given data as payload data.
     * If the bytes array's length is bigger than {@link Fragment#MAX_FRAGMENT_PAYLOAD_SIZE} it will be split up into multiple fragments.
     * By java's array limit if {@link Fragment#MAX_FRAGMENT_PAYLOAD_SIZE} is {@link Integer#MAX_VALUE} there will always be a single fragment
     * The {@link OpCode} determines what kind of fragment this will be, most often either {@link OpCode#TEXT_FRAME} or {@link OpCode#BINARY_FRAME}.
     *
     * @param opCode The type of fragment this will be
     * @param bytes  The data for the new fragment
     * @return A list with all fragments needed to wrap the data.
     */
    public static List<Fragment> withData(OpCode opCode, byte[] bytes) {
        List<Fragment> fragments = new ArrayList<>();

        //Check whether multiple fragments are needed. If this is a control message then it isn't allowed to be fragmented
        if (bytes.length > MAX_FRAGMENT_PAYLOAD_SIZE && !opCode.isControlOpCode) {

            //Cut the array into pieces of i to i + maxFragmentSize
            for (int i = 0; i < bytes.length; i += MAX_FRAGMENT_PAYLOAD_SIZE) {
                //endIndex is either i + maxFragmentSize of for the last fragment the last index
                int endIndex = Math.min(i + MAX_FRAGMENT_PAYLOAD_SIZE, bytes.length);
                fragments.add(new Fragment(opCode, Arrays.copyOfRange(bytes, i, endIndex)));

                //After the first fragment use the continuation opcode
                opCode = OpCode.CONTINUATION;
            }
        } else {
            fragments.add(new Fragment(opCode, bytes));
        }

        //Set the last fragment to fin
        fragments.get(fragments.size() - 1).fin = true;

        return fragments;
    }

    /**
     * Convert the fragments to a byte array according to <a href="https://tools.ietf.org/html/rfc6455#section-5.2">RFC-6455 section 5.2.</a> to send over the network.
     *
     * @return The byte[] representing the fragment
     */
    public byte[] toBytes() {
        int size = payloadData.length; //Always the payload data
        size += payloadData.length > 125 ? 2 : 0; //If the payload is bigger than 125 we need at least 2 more bytes
        size += payloadData.length > 65535 ? 6 : 0; //If it wouldn't fit in those bytes we need 6 more (total of 8)
        size += 6; //fin,rsv,opcode total of 1, mask + payloadLength total of 1, masking key total of 4. So in total 1+1+4=6 bytes

        //Create a ByteBuffer with the calculated size
        ByteBuffer buffer = ByteBuffer.allocate(size);

        //Set first bit if fin is true
        byte a = (byte) ((fin ? 1 << 7 : 0));

        //Set second, third and fourth bit if respectively rsv1, rsv2, and rsv3 are true
        a |= rsv1 ? 1 << 6 : 0;
        a |= rsv2 ? 1 << 5 : 0;
        a |= rsv3 ? 1 << 4 : 0;

        //Set the last 4 bits to the opcode bits
        a |= opCode.bits;

        //Put a in the buffer
        buffer.put(a);

        //Reset a and set the first bit if this fragment is masked
        a = (byte) (hasMask ? 1 << 7 : 0);

        //Write the payloadData length to the last 7 bits
        a |= payloadData.length > 125 ? (payloadData.length > 65535 ? 127 : 126) : payloadData.length;

        //Put the length and mask bit in
        buffer.put(a);

        //If payloadLength is bigger than 125 we either save it as a long or a short
        if (payloadData.length > 125) {
            //If it wouldn't fit in 16 bits (a short) save it as a long
            if (payloadData.length > (1 << 16) - 1) {
                buffer.putLong(payloadData.length);
            } else {
                buffer.putShort((short) payloadData.length);
            }
        }

        //Save the mask if there is one
        if (hasMask) {
            buffer.put(mask);

            //And mask the payloadData
            for (int i = 0; i < payloadData.length; i++) {
                buffer.put((byte) (payloadData[i] ^ mask[i % 4]));
            }
        } else {
            buffer.put(payloadData);
        }

        return buffer.array();
    }

    public enum OpCode {
        CONTINUATION((Byte) -> (Byte & 0x0F) == 0x0, (byte) 0x0),
        TEXT_FRAME((Byte) -> (Byte & 0x0F) == 0x1, (byte) 0x1),
        BINARY_FRAME((Byte) -> (Byte & 0x0F) == 0x2, (byte) 0x2),
        NON_CONTROL((Byte) -> {
            int val = (Byte & 0x0F);
            return val == 0x3 || val == 0x4 || val == 0x5 || val == 0x6 || val == 0x7 || val == 0xB || val == 0xC || val == 0xE || val == 0xF;
        }, (byte) 0x3),
        CONNECTION_CLOSE((Byte) -> (Byte & 0x0F) == 0x8, (byte) 0x8, true),
        PING((Byte) -> (Byte & 0x0F) == 0x9, (byte) 0x9, true),
        PONG((Byte) -> (Byte & 0x0F) == 0xA, (byte) 0xA, true);

        public final byte bits;
        public final boolean isControlOpCode;
        final Predicate<Byte> isOpCodePredicate;

        OpCode(Predicate<Byte> isOpcode, byte bits) {
            this(isOpcode, bits, false);
        }

        OpCode(Predicate<Byte> isOpcode, byte bits, boolean isControlOpCode) {
            this.isOpCodePredicate = isOpcode;
            this.bits = bits;
            this.isControlOpCode = isControlOpCode;
        }

        public static OpCode getOpcode(byte b) {
            for (OpCode value : OpCode.values()) {
                if (value.isOpCodePredicate.test(b)) return value;
            }
            return OpCode.NON_CONTROL;
        }
    }

}
