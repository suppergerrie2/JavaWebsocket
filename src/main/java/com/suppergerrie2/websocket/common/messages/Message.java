package com.suppergerrie2.websocket.common.messages;

import com.suppergerrie2.websocket.ProtocolErrorException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Message {

    private ArrayList<Fragment> fragments = new ArrayList<>();

    /**
     * Create a new message with given fragment.
     * This fragment is the first fragment in the message and will thus also determine the type.
     *
     * @param fragment The first fragment of this message. Cannot be a {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#CONTINUATION} frame
     * @throws ProtocolErrorException When the frame is a {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#CONTINUATION} frame.
     */
    public Message(Fragment fragment) throws ProtocolErrorException {
        if (fragment.opCode == Fragment.OpCode.CONTINUATION) {
            throw new ProtocolErrorException("First fragment cannot be a continuation frame!");
        }
        fragments.add(fragment);
    }

    /**
     * Create a binary message with the given binary data.
     * The first frame created will have the {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#BINARY_FRAME} opcode.
     *
     * @param bytes The payload data
     */
    public Message(byte[] bytes) {
        this(Fragment.OpCode.BINARY_FRAME, bytes);
    }

    /**
     * Create a text message with the given text data.
     * The first frame created will have the {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#TEXT_FRAME} opcode.
     *
     * @param data The payload data
     */
    public Message(String data) {
        this(Fragment.OpCode.TEXT_FRAME, data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Create a message with the given type and payload data.
     * The opCode cannot be {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#CONTINUATION}
     *
     * @param opCode      The type of this message
     * @param payloadData The payload data of this message
     */
    public Message(Fragment.OpCode opCode, byte[] payloadData) {
        if (opCode == Fragment.OpCode.CONTINUATION) {
            throw new IllegalArgumentException("Opcode cannot be continuation!");
        }

        fragments.addAll(Fragment.withData(opCode, payloadData));
    }

    /**
     * Add a fragment to the current message.
     * This cannot be done when the first fragment is a control message and the opcode has to be {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#CONTINUATION}
     *
     * @param fragment The fragment to add to this message
     * @throws ProtocolErrorException When the message already has atleast 1 fragment and this fragment is not a {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#CONTINUATION} fragment or this message is a control message.
     */
    public void addFragment(Fragment fragment) throws ProtocolErrorException {
        if (isControlMessage() || fragments.size() > 0 && fragment.opCode != Fragment.OpCode.CONTINUATION) {
            throw new ProtocolErrorException(String.format("Fragment cannot be added because %s",
                                                             isControlMessage() ? "message is a control message" : "frame is not continuation"));
        }

        fragments.add(fragment);
    }

    /**
     * Get all of the fragments in this message.
     *
     * @return A unmodifiable list of fragments.
     */
    public List<Fragment> getFragments() {
        return Collections.unmodifiableList(fragments);
    }

    /**
     * Checks whether this message is a control message.
     * This is the case if it only contains 1 fragment and this fragment has a control opcode.
     *
     * @return True if it is a control message.
     */
    public boolean isControlMessage() {
        //Control messages cannot be fragmented according to RFC 6455 Section 5.5.
        if (fragments.size() != 1) return false;

        return fragments.get(0).opCode.isControlOpCode;
    }

    /**
     * Gets the opcode of the first fragment, and thus the message type which is defined by the first fragment.
     *
     * @return The {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode} of the first fragment
     */
    public Fragment.OpCode getMessageType() {
        return fragments.get(0).opCode;
    }

    /**
     * Gets the paylaod data of all fragments in one byte array
     *
     * @return The byte array with all data from all fragments
     */
    public byte[] getPayloadData() {
        //If there is 1 fragment just return it
        if (fragments.size() == 1) return fragments.get(0).payloadData;

        //If there are more fragments calculate the total size
        int totalPayloadSize = fragments.stream().mapToInt(fragment -> fragment.payloadData.length).sum();

        //Allocate an array for it
        byte[] payload = new byte[totalPayloadSize];

        //And copy all of the fragment's data into the new array
        int lastIndex = 0;
        for (Fragment fragment : fragments) {
            System.arraycopy(fragment.payloadData, 0, payload, lastIndex, fragment.payloadData.length);
            lastIndex = lastIndex + fragment.payloadData.length;

        }

        return payload;
    }


    @Override
    public String toString() {
        if (fragments.size() > 0) {
            Fragment fragment = fragments.get(0);

            StringBuilder builder = new StringBuilder();

            if (fragment.fin) builder.append("{ FIN ");
            builder.append(fragment.opCode.toString()).append(" ");

            if (fragment.opCode == Fragment.OpCode.TEXT_FRAME) {
                builder.append(new String(fragment.payloadData, StandardCharsets.UTF_8));
            } else {
                for (byte b : fragment.payloadData) {
                    builder.append(String.format("|%02x|", b));
                }
            }
            builder.append(" }");
            return builder.toString();
        }

        return "Empty message";
    }

    public boolean isFragmented() {
        return fragments.size() > 1 || (fragments.size() > 0 && !fragments.get(0).fin);
    }
}
