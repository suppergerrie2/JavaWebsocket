package com.suppergerrie2.websocket.client;

import com.suppergerrie2.websocket.common.State;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;

public class ReadHandler implements CompletionHandler<Integer, ByteBuffer> {

    private final Client client;
    private ByteBuffer currentData;

    ReadHandler(Client client) {
        this.client = client;
    }

    @Override
    public void completed(Integer result, ByteBuffer attachment) {
        if (result > 0) {
            //Receive fragments when state is open or closing (so the client can read the final close message)
            if (client.getState() == State.OPEN || client.getState() == State.CLOSING) {
                //Parse fragments


                //Get all bytes received
                attachment.flip();
                byte[] bytes = new byte[attachment.remaining()];
                attachment.get(bytes);
                appendData(bytes);
            } else {
                //Parse HTTP header
                attachment.flip();

                //Get all bytes received
                byte[] bytes = new byte[attachment.remaining()];
                attachment.get(bytes);

                //Try and find the end \r\n\r\n
                int index;
                boolean found = false;
                for (index = 0; index < bytes.length; index++) {
                    if (index >= 4 &&
                            bytes[index - 1] == (byte) '\n' &&
                            bytes[index - 2] == (byte) '\r' &&
                            bytes[index - 3] == (byte) '\n' &&
                            bytes[index - 4] == (byte) '\r'
                    ) {
                        found = true;
                        break;
                    }
                }

                //Reset the position
                attachment.position(0);

                //If the ending sequence is found
                if (found) {
                    //Copy the data from the beginning of the received data to the ending sequence and add it to the already received data
                    bytes = new byte[index];
                    attachment.get(bytes, 0, index);
                    appendData(bytes);

                    //Let the client handle this data
                    client.handleData(currentData);

                    //Set position to the end of the ending sequence so the remaining data can be copied.
                    attachment.position(index);

                    //Copy the data
                    bytes = new byte[attachment.limit() - index];
                    attachment.get(bytes);
                }

                //Append the (remaining) data
                appendData(bytes);
            }
        }

        //If bytes were received and the buffer was not completely filled at least 1 message has been completely received
        if (result != -1 && result < attachment.capacity()) {
            //Let the client handle the data
            client.handleData(currentData);

            //Clear the data, it has been handled
            currentData.clear();
        }

        if (result == -1) {
            client.setState(State.CLOSED);
        }

        //As long as the client is connected keep reading
        if (client.isConnected()) {

            client.startReading();
        } else {
            System.out.println("Closing reader!");
        }
    }

    /**
     * Append the given data to the currentData buffer
     *
     * @param data The data to append
     */
    private void appendData(byte[] data) {
        try {
            if (currentData == null) {
                currentData = ByteBuffer.wrap(data);
                currentData.position(currentData.limit());
            } else {
                if (currentData.capacity() - currentData.position() < data.length) {
                    ByteBuffer newBuffer = ByteBuffer.allocate(currentData.capacity() + data.length);
                    for (int i = 0; i < currentData.position(); i++) {
                        newBuffer.put(currentData.array()[i]);
                    }
                    currentData = newBuffer;
                }

                currentData.put(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void failed(Throwable exc, ByteBuffer attachment) {
        exc.printStackTrace();
    }
}
