package com.suppergerrie2.websocket.client;

import com.suppergerrie2.websocket.common.State;
import com.suppergerrie2.websocket.common.messages.Fragment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

class ClientTest {

    boolean receivedMessage;
    @Test
    void echoTextTest() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {
        //Create the string to send
        StringBuilder s = new StringBuilder();
        s.append("START");
        for (int i = 0; i < 1000 * 4; i++) {
            s.append(i % 10);
        }
        s.append("END");
        final String toSend = s.toString();

        System.out.println("Connecting...");

        Client client = new Client(new URL("ws://echo.websocket.org/"));
        receivedMessage = false;

        client.registerMessageHandler("", message -> {

            receivedMessage = true;
            System.out.println("Received message closing client.");
            client.stop();

            Assertions.assertEquals(message.getMessageType(), Fragment.OpCode.TEXT_FRAME);

            switch (message.getMessageType()) {
                case TEXT_FRAME:
                    String received = new String(message.getPayloadData(), StandardCharsets.UTF_8);
                    System.out.println(received);
                    Assertions.assertEquals(toSend, received);
                    break;
                case BINARY_FRAME:
                    System.out.println(Arrays.toString(message.getPayloadData()));
                    break;
            }
        });

        client.start();
        System.out.println("Client should be connected now!");

        //Wait for handshake to finish
        while (client.getState() == State.HANDSHAKE) Thread.sleep(10);
        System.out.println("Client has finished the handshake");

        //Send it
        client.send(toSend);

        //Keep program running as long as client is connected
        while (client.isConnected()) Thread.sleep(10);
        Assertions.assertTrue(receivedMessage, "No message received");
        System.out.println("Client disconnected! bye bye!");
    }

    @Test
    void echoBinaryTest() throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException {
        //Create the string to send
        final byte[] toSend = new byte[1000 * 4];

        for (int i = 0; i < toSend.length; i++) {
            toSend[i] = (byte) i;
        }

        System.out.println("Connecting...");

        Client client = new Client(new URL("ws://echo.websocket.org/"));

        receivedMessage = false;
        client.registerMessageHandler("", message -> {

            receivedMessage = true;
            Assertions.assertEquals(message.getMessageType(), Fragment.OpCode.BINARY_FRAME);

            switch (message.getMessageType()) {
                case TEXT_FRAME:
                    System.out.println(new String(message.getPayloadData(), StandardCharsets.UTF_8));
                    break;
                case BINARY_FRAME:
                    System.out.println(Arrays.toString(message.getPayloadData()));
                    Assertions.assertArrayEquals(toSend, message.getPayloadData());
                    break;
            }

            client.stop();
        });

        client.start();
        System.out.println("Client should be connected now!");

        //Wait for handshake to finish
        while (client.getState() == State.HANDSHAKE) Thread.sleep(10);
        System.out.println("Client has finished the handshake");

        //Send it
        client.send(toSend);

        //Keep program running as long as client is connected
        while (client.isConnected()) Thread.sleep(10);
        Assertions.assertTrue(receivedMessage, "Never received a message");
        System.out.println("Client disconnected! bye bye!");
    }
}