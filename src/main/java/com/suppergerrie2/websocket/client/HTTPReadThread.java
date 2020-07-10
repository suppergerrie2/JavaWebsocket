package com.suppergerrie2.websocket.client;

import com.suppergerrie2.websocket.ExtendedInputStream;
import com.suppergerrie2.websocket.ProtocolErrorException;
import com.suppergerrie2.websocket.common.Constants;

import java.io.IOException;
import java.io.InputStream;

public class HTTPReadThread extends Thread {

    final ExtendedInputStream inputStream;
    final Client client;

    public HTTPReadThread(Client client, InputStream inputStream) {
        this.inputStream = new ExtendedInputStream(inputStream);
        this.client = client;
    }

    @Override
    public void run() {
        StringBuilder httpHeaderBuilder = new StringBuilder();
        while (client.getState() == com.suppergerrie2.websocket.common.State.HANDSHAKE) {
            try {
                String line = inputStream.readLine();

                if (line.charAt(0) == '\r' && line.charAt(1) == '\n') {
                    client.parseHandshakeHeader(httpHeaderBuilder.toString());
                }

                httpHeaderBuilder.append(line);
            } catch (IOException e) {
                e.printStackTrace();
                client.stop(Constants.StatusCode.UNEXPECTED_EXCEPTION, true);
                return;
            } catch (ProtocolErrorException e) {
                e.printStackTrace();
                client.stop(e.statusCode, true);
            }
        }
    }
}
