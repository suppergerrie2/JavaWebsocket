package com.suppergerrie2.websocket.client;

import com.suppergerrie2.websocket.common.State;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("BusyWait")
public class AutobahnTestSuite {

    final String agent = "java-websocket-client-1";
    final String baseURL = "ws://127.0.0.1:9002";
    int caseCounts;
    boolean running;

    @Test
    public void runAutobahnTestSuite() throws IOException, InterruptedException, URISyntaxException {
        running = true;

        Client client = new Client(new URI(baseURL + "/getCaseCount"));

        client.registerMessageHandler("", (message -> {
            String data = new String(message.getPayloadData(), StandardCharsets.UTF_8);
            caseCounts = Integer.parseInt(data);
            System.out.printf("Received %d case counts%n", caseCounts);
        }));

        client.registerCloseHandler((c) -> {
            System.out.println("Finished receiving case counts");
            startTesting();
        });

        client.start();

        while (running) Thread.sleep(10);
    }

    void startTesting() {
        for (int i = 0; i < caseCounts; i++) {
            System.out.println();
            doTestCase(i + 1);
        }

        System.out.println();
        updateReports();

        running = false;
    }

    void doTestCase(int currentCaseCount)  {
        try {
            System.out.println("Starting test case " + currentCaseCount);
            Client client = new Client(
                    new URI(String.format("%s/runCase?case=%d&agent=%s", baseURL, currentCaseCount, agent)));

            client.registerMessageHandler("", message -> {
                switch (message.getMessageType()) {
                    case TEXT_FRAME:
                        client.send(new String(message.getPayloadData(), StandardCharsets.UTF_8));
                        break;
                    default:
                    case BINARY_FRAME:
                        client.send(message.getPayloadData());
                        break;
                }
            });

            client.registerCloseHandler((c) -> System.out.printf("Case %d done%n", currentCaseCount));

            client.start();
            while (client.getState() == State.HANDSHAKE || client.isConnected()) Thread.sleep(10);

        } catch (IOException | InterruptedException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void updateReports() {
        try {
            System.out.println("updating reports");
            Client client = new Client(new URI(String.format("%s/updateReports?agent=%s", baseURL, agent)));
            client.registerCloseHandler(c -> System.out.println("finished update reports"));
            client.start();

            while (client.getState() != State.CLOSED) Thread.sleep(10);
        } catch (IOException | InterruptedException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

}
