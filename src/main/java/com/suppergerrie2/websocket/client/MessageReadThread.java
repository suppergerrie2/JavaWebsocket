package com.suppergerrie2.websocket.client;

import com.suppergerrie2.websocket.ExtendedInputStream;
import com.suppergerrie2.websocket.ProtocolErrorException;
import com.suppergerrie2.websocket.common.Constants;
import com.suppergerrie2.websocket.common.messages.Fragment;
import com.suppergerrie2.websocket.common.messages.Message;

import java.io.IOException;
import java.io.InputStream;

public class MessageReadThread extends Thread {

    final ExtendedInputStream inputStream;
    final Client client;

    public MessageReadThread(Client client, InputStream inputStream) {
        this.inputStream = new ExtendedInputStream(inputStream);
        this.client = client;
    }

    @Override
    public void run() {
        while (client.getState() == com.suppergerrie2.websocket.common.State.HANDSHAKE || client.getState() == com.suppergerrie2.websocket.common.State.CLOSED) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            Message currentMessage = null;
            while (client.isConnected()) {
                Fragment fragment;

                try {
                    fragment = new Fragment(inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                    client.stop(Constants.StatusCode.UNEXPECTED_EXCEPTION);
                    return;
                } catch (ProtocolErrorException e) {
                    e.printStackTrace();
                    client.stop(Constants.StatusCode.PROTOCOL_ERROR);
                    return;
                }

                if (fragment.opCode.isControlOpCode) {
                    Message message = new Message(fragment);
                    client.handleMessage(message);
                    continue;
                } else if (currentMessage == null) {
                    currentMessage = new Message(fragment);
                } else {
                    currentMessage.addFragment(fragment);
                }

                if (fragment.fin) {
                    client.handleMessage(currentMessage);
                    currentMessage = null;
                }
            }
        } catch (ProtocolErrorException e) {
            e.printStackTrace();
            client.stop(Constants.StatusCode.PROTOCOL_ERROR);
        }
    }
}
