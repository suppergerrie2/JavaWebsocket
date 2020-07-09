package com.suppergerrie2.websocket.client;

import com.suppergerrie2.websocket.ProtocolErrorException;
import com.suppergerrie2.websocket.common.Constants;
import com.suppergerrie2.websocket.common.Helpers;
import com.suppergerrie2.websocket.common.State;
import com.suppergerrie2.websocket.common.WebsocketURLStreamHandlerFactory;
import com.suppergerrie2.websocket.common.messages.Fragment;
import com.suppergerrie2.websocket.common.messages.Message;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class Client {

    static {
        WebsocketURLStreamHandlerFactory.initialize();
    }

    private final URL host;
    private Socket socket;
    private State state = State.CLOSED;
    private byte[] randomBytes;

    private HashMap<String, List<Consumer<Message>>> messageHandlers = new HashMap<>();
    private List<Consumer<Client>> closeHandlers = new ArrayList<>();

    private String[] activeProtocols;

    public Client(URL host) throws ProtocolException {
        if (!(host.getProtocol().equals("ws") || host.getProtocol().equals("wss"))) {
            throw new ProtocolException("Only supports ws and wss protocols");
        }

        this.host = host;
    }

    public void registerMessageHandler(String protocol, Consumer<Message> handler) {
        if (!messageHandlers.containsKey(protocol)) {
            messageHandlers.put(protocol, new ArrayList<>());
        }

        messageHandlers.get(protocol).add(handler);
    }

    public void registerCloseHandler(Consumer<Client> handler) {
        closeHandlers.add(handler);
    }

    public void start() throws IOException {

        int port = host.getPort();
        if (port == -1) {
            port = host.getDefaultPort();
        }

        //If we use secure websocket create a SSL socket
        if (host.getProtocol().equals("wss")) {
            SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(host.getHost(), port);
            sslSocket.startHandshake();
            socket = sslSocket;
        } else {
            socket = new Socket(host.getHost(), port);
        }

        startReading();
        doInitializeWebsocketUpgrade();
    }

    private void doInitializeWebsocketUpgrade() throws IOException {
        setState(State.HANDSHAKE);
        String[] headers = new String[]{
                String.format("GET %s HTTP/1.1", host.toExternalForm()), //TODO: Fix port being added twice.
                "Connection: Upgrade",
                String.format("Sec-WebSocket-Key: %s", getNonce()),
                String.format("Host: %s:%s", host.getHost(),
                              host.getPort() == -1 ? host.getDefaultPort() : host.getPort()),
                "Upgrade: websocket",
                "Sec-WebSocket-Version: 13",
                String.format("Sec-WebSocket-Protocol: %s", String.join(",", messageHandlers.keySet()))
        };

        String header = String.join("\r\n", headers) + "\r\n\r\n";

        byte[] bytes = header.getBytes(StandardCharsets.UTF_8);

        socket.getOutputStream().write(bytes);
    }

    /**
     * Generates a nonce if none is generated already.
     *
     * @return The 16 byte nonce
     */
    private String getNonce() {
        if (randomBytes == null) {
            randomBytes = new byte[16];
            Random random = new Random();
            random.nextBytes(randomBytes);
        }

        return Base64.getEncoder().encodeToString(randomBytes);
    }

    void startReading() throws IOException {
        try {
            new MessageReadThread(this, socket.getInputStream()).start();
        } catch (IOException e) {
            e.printStackTrace();
            stop(Constants.StatusCode.UNEXPECTED_EXCEPTION, true);
        }

        new HTTPReadThread(this, socket.getInputStream()).start();
    }

    /**
     * Returns true when the client is connected to the remote host.
     * This does not mean the client is ready to send and receive, for that check if the {@link State} is {@link State#OPEN}.
     *
     * @return true if the client is connected to a remote host
     * @see Client#getState()
     */
    public boolean isConnected() {
        return getState() != State.CLOSED;
    }

    /**
     * Get the current {@link State} of the client.
     *
     * @return The current {@link State}
     */
    public State getState() {
        return state;
    }

    void setState(State state) {
        this.state = state;

        if (state == State.CLOSED) {
            for (Consumer<Client> handler : closeHandlers) {
                handler.accept(this);
            }

            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void handleCloseMessage(final Message message) throws ProtocolErrorException {
        byte[] payloadData = message.getPayloadData();

        //If the client is not closing already and it receives a connection_close message, send one back
        if (getState() != State.CLOSING) {
            Constants.StatusCode statusCode = Constants.StatusCode.EXPECTS_STATUS_CODE;
            String closeReason = "";

            //If there is payload data read it to determine the reason
            if (payloadData.length >= 2) {
                statusCode = Constants.StatusCode.fromInteger((((payloadData[0] & 0xFF) << 8) | (payloadData[1] & 0xFF)));

                //@formatter:off
                if(statusCode == Constants.StatusCode.INVALID_STATUS_CODE) throw new ProtocolErrorException(String.format("Received close frame with invalid status code %d", ((payloadData[0] & 0xFF) << 8) | (payloadData[1] & 0xFF)));
                if(statusCode == Constants.StatusCode.EXPECTS_STATUS_CODE) throw new ProtocolErrorException("Received close frame with status code 1005");
                if(statusCode == Constants.StatusCode.EXPECTS_STATUS_CODE_ABNORMALLY) throw new ProtocolErrorException("Received close frame with status code 1005");
                if(statusCode == Constants.StatusCode.RESERVED) throw new ProtocolErrorException("Received close frame with status code 1005");
                //@formatter:on

                if(payloadData.length > 2) {
                    byte[] reasonBytes = Arrays.copyOfRange(payloadData, 2, payloadData.length);

                    if(!Helpers.isValidUTF8(reasonBytes, false)) {
                        throw new ProtocolErrorException("Received non UTF-8 data in close reason", Constants.StatusCode.INCONSISTENT_DATA_TYPE);
                    }

                    closeReason = new String(reasonBytes, StandardCharsets.UTF_8);
                }
            }

            System.out.printf("Received close connection message with status code %s (%s)%n", statusCode.name(), statusCode.value);
            if(!closeReason.isEmpty()) System.out.println(closeReason);

            //Send a close message back
            sendMessage(new Message(Fragment.OpCode.CONNECTION_CLOSE, payloadData));
        } else {
            System.out.println("Closed connection!");
        }

        //Connection has been closed, so update the client's state
        setState(State.CLOSED);
    }

    void handleMessage(final Message message) {
        try {
            //If the message is a control message the client needs to handle it
            if (message.isControlMessage()) {
                byte[] payloadData = message.getPayloadData();

                if (payloadData.length > 125 || message.isFragmented()) {
                    //@formatter:off
                    throw new ProtocolErrorException(
                            message.isFragmented() ? "Control message cannot be fragmented. (RFC-6455 Section 5.5.)" :
                                    String.format("Control message cannot have a payload size of 126 or more but has %d. (RFC-6455 Section 5.5.)", payloadData.length));
                    //@formatter:on
                }

                switch (message.getMessageType()) {
                    case CONNECTION_CLOSE:
                       handleCloseMessage(message);
                        break;
                    case PING:

                        System.out.println("Received ping!");
                        Message response = new Message(Fragment.OpCode.PONG, payloadData);
                        sendMessage(response);

                        break;
                    case PONG:
                        System.out.println("Received pong!");
                        break;
                    default:
                        throw new UnsupportedOperationException(
                                String.format("Don't now how to handle unknown control opcode %s",
                                              message.getMessageType()));
                }

            } else {
                for (String protocol : activeProtocols) {
                    //Pass it to the user
                    for (Consumer<Message> h : messageHandlers.get(protocol)) {
                        h.accept(message);
                    }
                }
            }
        } catch (ProtocolErrorException e) {
            e.printStackTrace();
            this.stop(e.statusCode, true);
        }
    }

    /**
     * Send a byte array over the network.
     * This will be encoded in a {@link Fragment} of type {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#TEXT_FRAME}
     *
     * @param bytes The bytes to send
     * @see Client#send(String)
     */
    public void send(byte[] bytes) {
        Message message = new Message(bytes);
        sendMessage(message);
    }

    /**
     * Send a string over the network.
     * this will be encoded in a {@link Fragment} of type {@link com.suppergerrie2.websocket.common.messages.Fragment.OpCode#TEXT_FRAME}.
     *
     * @param s The string to send
     * @see Client#send(byte[])
     */
    public void send(String s) {
        Message message = new Message(s);
        sendMessage(message);
    }

    private void sendMessage(Message message) {
        //Make sure the message can be send.
        //Can only send messages in the open state or a close message in the connection close state.
        if (getState() != State.OPEN && !(message
                .getMessageType() == Fragment.OpCode.CONNECTION_CLOSE && getState() == State.CLOSING)) {
            throw new IllegalStateException(
                    String.format("Can only send messages in open state, but client is in %s state", getState()));
        }

        //Send the fragments
        for (Fragment fragment : message.getFragments()) {
            //TODO: Make it async
            try {
                socket.getOutputStream().write(fragment.toBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void parseHandshakeHeader(String headerString) {
        String[] header = headerString.split("\r\n");
        String[] statusLine = header[0].split(" ");

        //Make sure it starts with http
        if (statusLine[0].startsWith("HTTP")) {

            //Get the statusCode
            int statusCode;
            try {
                statusCode = Integer.parseInt(statusLine[1]);
            } catch (NumberFormatException e) {
                //If it is not a number throw an exception
                throw new IllegalArgumentException("First line of response should have a http status code!");
            }

            //Status code should be 101
            if (statusCode != 101) {
                throw new IllegalStateException(String.format("status could should be 101 but is %d", statusCode));
            }

            //Collect headers and put them in a map TODO: some headers are allowed to occur more than once and should be merged into one
            HashMap<String, String> headerFields = new HashMap<>();
            for (int i = 1; i < header.length; i++) {
                String[] keyValue = header[i].split(":");
                headerFields.put(keyValue[0].toLowerCase(), keyValue[1].trim());
            }

            //Check if the upgrade field, connection field and sec-websocket-accept field are correct
            if (!headerFields.getOrDefault("upgrade", "").toLowerCase().equals("websocket")
                    || !headerFields.getOrDefault("connection", "").toLowerCase().equals("upgrade")
                    || !headerFields.getOrDefault("sec-websocket-accept", "")
                                    .equals(Helpers.getSecWebsocket(getNonce()))) {

                System.out.printf("upgrade %s expected websocket%n",
                                  headerFields.getOrDefault("upgrade", "").toLowerCase());
                System.out.printf("connection %s expected upgrade%n",
                                  headerFields.getOrDefault("connection", "").toLowerCase());
                System.out.printf("sec-websocket-accept %s expected %s%n",
                                  headerFields.getOrDefault("sec-websocket-accept", "").toLowerCase(),
                                  Helpers.getSecWebsocket(getNonce()));
                throw new IllegalStateException("Header had an invalid value");
            }

            String protocols = headerFields.getOrDefault("Sec-WebSocket-Protocol", "");
            String[] protocolArray = Arrays.stream(protocols.split(",")).map(String::trim).toArray(String[]::new);

            for (String protocol : protocolArray) {
                if (protocol.length() == 0) continue;

                if (!messageHandlers.containsKey(protocol)) {
                    throw new IllegalStateException(
                            String.format("Server requested protocol %s which client cannot handle", protocol));
                }
            }

            activeProtocols = protocolArray;

            //handshake is done, state is open now
            setState(State.OPEN);
        } else {
            throw new IllegalStateException(
                    String.format("Should have received a HTTP response but it is %s", headerString));
        }
    }

    /**
     * Stop the client, sends a connection close message when state is open.
     * Can only be called when state is {@link State#OPEN}
     *
     * @throws IllegalStateException when state is not {@link State#OPEN}
     */
    public void stop() {
        stop(1000, false);
    }

    public void stop(Constants.StatusCode statusCode, boolean forceStop) {
        stop(statusCode.value, forceStop);
    }

    public void stop(int statusCode, boolean forceStop) {
        if (getState() == State.OPEN) {
            setState(State.CLOSING);
            try {
                sendMessage(new Message(Fragment.withData(Fragment.OpCode.CONNECTION_CLOSE, new byte[]{
                        (byte) ((statusCode >> 8) & 0xFF),
                        (byte) ((statusCode) & 0xFF)
                }).get(0)));

            } catch (ProtocolErrorException e) {
                e.printStackTrace();
                // We failed while failing, only the force can stop us now.
                forceStop = true;
            }

            if (forceStop) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                setState(State.CLOSED);
            }
        } else {
            throw new IllegalStateException("Cannot stop client that hasn't started yet");
        }
    }
}
