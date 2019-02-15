package ru.bobrov.vyacheslav.console_chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class ConnectionsHolder implements Runnable {
    private static final int BUFFER_SIZE = 512;
    private final ByteBuffer buffer;
    @Value("${server_port:9877}")
    private int port;

    @Autowired
    private MessageQueue messageQueue;

    private ServerSocket serverSocket;
    private Selector selector;

    public ConnectionsHolder(MessageQueue messageQueue) {
        buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        messageQueue.addNewMessageListener(() -> selector.wakeup());
    }

    @Override
    public void run() {
        try {
            initServerSocket();
            while (!Thread.interrupted()) {
                processConnections();
                processCommands();
            }
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
        }
    }

    private void processConnections() throws IOException {
        if (selector.select()==0) {
            return;
        }

        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectedKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey selectedKey = iterator.next();
            iterator.remove();

            if (selectedKey.isAcceptable())
                acceptConnection(selectedKey);
            if (selectedKey.isReadable())
                readSocket(selectedKey);
        }
    }

    private void processCommands() throws IOException {
        Message message = messageQueue.pollServerMessages();
        if (message==null)
            return;
        switch (message.getMessageType()) {
            case WRITE:
                write(message.getSocketAddress(), message.getMessage());
                break;

            case KILL:
            case DISCONNECTED:
                SelectableChannel channel = selector.keys().stream()
                        .filter(selectionKey -> selectionKey.attachment().equals(message.getSocketAddress()))
                        .findFirst().orElseThrow(IOException::new).channel();
                channel.close();
                break;

            case BROADCAST:
                buffer.clear();
                buffer.put(message.getMessage().getBytes(StandardCharsets.UTF_8));
                for (SelectionKey selectionKey : selector.keys()) {
                    if (!(selectionKey.channel() instanceof ServerSocketChannel)) {
                        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                        buffer.flip();
                        socketChannel.write(buffer);
                    }
                }
                buffer.clear();
                break;
        }
    }

    private void write(final SocketAddress socketAddress, final String message) throws IOException {
        SelectableChannel channel = selector.keys().stream()
                .filter(selectionKey
                        -> !Objects.isNull(selectionKey.attachment()) && selectionKey.attachment().equals(socketAddress))
                .findFirst().orElseThrow(IOException::new).channel();

        SocketChannel socketChannel = (SocketChannel) channel;
        buffer.clear();
        buffer.put(message.getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        socketChannel.write(buffer);
    }

    private void readSocket(SelectionKey selectedKey) throws IOException {
        SocketChannel socketChannel = (SocketChannel) selectedKey.channel();
        SocketAddress socketAddress = (SocketAddress) selectedKey.attachment();

        if (socketChannel.read(buffer)==-1) {
            selectedKey.cancel();
            socketChannel.close();
            log.info("Соединение: " + socketChannel.socket() + " разорвано");

            sendMessage(socketAddress, Message.MessageType.DISCONNECTED);
            return;
        }
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.clear();

        String text = new String(bytes, StandardCharsets.UTF_8);
        log.debug("Получено: " + text);
        sendMessage(socketAddress, Message.MessageType.READ, text);
    }

    private void acceptConnection(SelectionKey selectedKey) {
        Socket socket;
        SocketChannel socketChannel;

        try {
            socket = serverSocket.accept();
            log.info("Установлено соединение с:" + socket);
            socketChannel = socket.getChannel();
            if (socketChannel!=null) {
                SocketAddress socketAddress = socket.getRemoteSocketAddress();
                socketChannel.configureBlocking(false);
                socketChannel.register(selector, SelectionKey.OP_READ, socketAddress);

                sendMessage(socketAddress, Message.MessageType.CONNECTED);
            }
        } catch (IOException e) {
            log.error(e.getLocalizedMessage(), e);
            selectedKey.cancel();
        }
    }

    private void sendMessage(SocketAddress socketAddress, Message.MessageType messageType) {
        sendMessage(socketAddress, messageType, null);
    }

    private void sendMessage(SocketAddress socketAddress, Message.MessageType messageType, String messageText) {
        Message message = Message.builder()
                .socketAddress(socketAddress)
                .messageType(messageType)
                .message(messageText)
                .build();
        try {
            messageQueue.addConnectionMessage(message);
        } catch (InterruptedException e) {
            log.warn("Ожидание добавления сообщения в очередь, прервано");
            Thread.currentThread().interrupt();
        }
    }

    private void initServerSocket() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocket = serverSocketChannel.socket();
        serverSocket.bind(new InetSocketAddress(port));
        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }
}
