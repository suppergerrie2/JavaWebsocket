package com.suppergerrie2.websocket.client;

import com.suppergerrie2.websocket.common.Constants;
import com.suppergerrie2.websocket.common.Helpers;
import com.suppergerrie2.websocket.common.State;
import com.suppergerrie2.websocket.common.WebsocketURLStreamHandlerFactory;
import com.suppergerrie2.websocket.common.messages.Fragment;
import com.suppergerrie2.websocket.common.messages.Message;
import tlschannel.ClientTlsChannel;
import tlschannel.TlsChannel;
import tlschannel.async.AsynchronousTlsChannel;
import tlschannel.async.AsynchronousTlsChannelGroup;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class Client {

    static {
        WebsocketURLStreamHandlerFactory.initialize();
    }

    private final URL host;
    private final ReadHandler readHandler;

    private AsynchronousByteChannel channel;
    private State state = State.CLOSED;
    private byte[] randomBytes;
    private Message currentMessage;

    private HashMap<String, List<Consumer<Message>>> messageHandlers = new HashMap<>();
    private List<Consumer<Client>> closeHandlers = new ArrayList<>();

    private String[] activeProtocols;

    public Client(URL host) throws ProtocolException {
        if (!(host.getProtocol().equals("ws") || host.getProtocol().equals("wss"))) {
            throw new ProtocolException("Only supports ws and wss protocols");
        }

        this.host = host;
        readHandler = new ReadHandler(this);
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

    public void start() throws IOException, NoSuchAlgorithmException, ExecutionException, InterruptedException {

        int port = host.getPort();
        if (port == -1) {
            port = host.getDefaultPort();
        }
        InetSocketAddress address = new InetSocketAddress(host.getHost(), port);

        //If we use secure websocket create a SSL channel
        if (host.getProtocol().equals("wss")) {
            SocketChannel rawChannel = SocketChannel.open();

            rawChannel.connect(address);

            rawChannel.configureBlocking(false);

            AsynchronousTlsChannelGroup channelGroup = new AsynchronousTlsChannelGroup();

            SSLContext sc = SSLContext.getDefault();

            TlsChannel tlsChannel = ClientTlsChannel.newBuilder(rawChannel, sc)
                                                    .build();

            channel = new AsynchronousTlsChannel(channelGroup, tlsChannel, rawChannel);
        } else {
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();

            channel.connect(address).get();

            this.channel = channel;
        }

        startReading();
        doInitializeWebsocketUpgrade();
    }

    private void doInitializeWebsocketUpgrade() throws ExecutionException, InterruptedException {
        setState(State.HANDSHAKE);
        String[] headers = new String[]{
                String.format("GET %s HTTP/1.1", host.toExternalForm()),
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

          channel.write(ByteBuffer.wrap(bytes));
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

    void startReading() {
        startReading(1024);
    }

    @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
    void startReading(int size) {
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        channel.read(buffer, buffer, readHandler);
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
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void handleData(ByteBuffer currentData) {
        if (getState() == State.CLOSED) {
            throw new IllegalStateException("cannot receive messages while in closed state");
        }

        if (getState() == State.HANDSHAKE) {
            //Get the handshake data
            currentData.flip();
            byte[] bytes = new byte[currentData.limit()];
            currentData.get(bytes);
            currentData.flip();

            //Convert it into a string
            String string = new String(bytes, StandardCharsets.UTF_8);

            //Parse it!
            try {
                parseHandshakeHeader(string);
            } catch (Exception e) {
                System.err.println("Failed to parse handshake headers! " + string);
                e.printStackTrace();
            }
        } else {
            //Deserialize the data into a fragment
            currentData.flip();
            Fragment fragment = new Fragment(currentData);
            currentData.flip();

            //If there is not already a message being created create one.
            if (currentMessage == null) {
                currentMessage = new Message(fragment);
            } else {
                //Else append the fragment to the current message.
                currentMessage.addFragment(fragment);
            }

            //If this is a fin fragment handle the message.
            if (fragment.fin) {
                handleMessage(currentMessage);
                currentMessage = null;
            }
        }
    }

    private void handleMessage(final Message message) {
        //If the message is a control message the client needs to handle it
        if (message.isControlMessage()) {
            byte[] payloadData = message.getPayloadData();

            if(payloadData.length > 125 || message.isFragmented()) {
                this.stop(Constants.StatusCode.PROTOCOL_ERROR);
                return;
            }

            switch (message.getMessageType()) {
                case CONNECTION_CLOSE:
                    //If the client is not closing already and it receives a connection_close message, send one back
                    if (getState() != State.CLOSING) {
                        //If there is payload data read it to determine the reason
                        if (payloadData.length != 0) {
                            System.out.println(
                                    String.format("Received close connection message with status code %s",
                                                  (((payloadData[0] & 0xFF) << 8) | (payloadData[1] & 0xFF))));

                            if(payloadData.length > 2) System.out.print("Close reason from other side: ");

                            for (int i = 2; i < payloadData.length; i++) {
                                System.out.print((char) payloadData[i]);
                            }

                            if(payloadData.length > 2) System.out.print(System.lineSeparator());
                        } else {
                            System.out.println("Received close connection message without reason");
                        }

                        //Send a close message back
                        sendMessage(new Message(Fragment.OpCode.CONNECTION_CLOSE, payloadData));
                    } else {
                        System.out.println("Closed connection!");
                    }

                    //Connection has been closed, so update the client's state
                    setState(State.CLOSED);
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
            ByteBuffer buffer = ByteBuffer.wrap(fragment.toBytes());

            //TODO: Make it async
            try {
                channel.write(buffer).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseHandshakeHeader(String headerString) {
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

    public void stop(Constants.StatusCode statusCode) {
        stop(statusCode.value, statusCode == Constants.StatusCode.PROTOCOL_ERROR);
    }

    public void stop(int statusCode, boolean forceStop) {
        if (getState() == State.OPEN) {
            setState(State.CLOSING);
            sendMessage(new Message(Fragment.withData(Fragment.OpCode.CONNECTION_CLOSE, new byte[] {
                    (byte) ((statusCode >> 8) & 0xFF),
                    (byte) ((statusCode ) & 0xFF)
            }).get(0)));

            if(forceStop) {
                try {
                    channel.close();
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
