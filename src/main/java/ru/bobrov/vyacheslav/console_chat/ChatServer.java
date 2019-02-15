package ru.bobrov.vyacheslav.console_chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ChatServer implements Runnable {
    @Autowired
    private MessageQueue queue;

    private final Map<SocketAddress, User> userMap = new HashMap<>();
    private final Map<String, User> loginMap = new HashMap<>();

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        while (!Thread.interrupted()) {
            Message message;
            try {
                message = queue.takeConnectionMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                continue;
            }

            SocketAddress socketAddress = message.getSocketAddress();
            String text = message.getMessage();

            switch (message.getMessageType()) {
                case CONNECTED:
                    connect(socketAddress);
                    break;

                case READ:
                    User connectedUser = userMap.get(socketAddress);
                    if (connectedUser==null) {
                        log.error("Сокет: " + socketAddress + " не был зарегистрирован!");
                        continue;
                    }
                    switch (connectedUser.getState()) {
                        case CONNECTED:
                            login(connectedUser, text.trim());
                            break;

                        case LOGGED:
                            try {
                                process(connectedUser, text);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            break;

                        case BANNED:
                        case DISCONNECTED:
                            log.error("Пользователь:" + connectedUser.getLogin() + " не подключен");
                            break;

                        default:
                            log.error("Неизвестный статус пользователя: " + connectedUser.getState());
                            break;
                    }
            }
        }
    }

    private void process(final User user, final String message) throws InterruptedException {
        if (StringUtils.isEmpty(message))
            return;

        if ("list".equals(message.trim().toLowerCase())) {
            String listLoggedUsers = loginMap.entrySet().stream()
                    .filter(stringUserEntry -> stringUserEntry.getValue().getState().equals(UserState.LOGGED))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining(", "));

            queue.addServerMessage(Message.builder()
                    .messageType(Message.MessageType.WRITE)
                    .message("В чате: " + listLoggedUsers+"\r\n")
                    .socketAddress(user.getSocketAddress())
                    .build());

            return;
        }

        String[] command = message.split("\\s+");
        if (command.length==1) {
            if (message.equals("bye"))
                bye(user);

            broadcast(user.getLogin() + ":\t" + message);
        } else if (command[0].equals("bye")) {
            bye(user, message.replaceFirst("bye", ""));
            broadcast("Пользователь " + user.getLogin() + " вышел из чата");
        } else {
            broadcast(user.getLogin() + ": " + message);
        }

    }

    private void bye(final User user) throws InterruptedException {
        bye(user, null);
    }

    private void bye(final User user, final String message) throws InterruptedException {
        queue.addServerMessage(Message.builder()
                .messageType(Message.MessageType.DISCONNECTED)
                .socketAddress(user.getSocketAddress())
                .build());

        broadcast("Пользователь " + user.getLogin() + " вышел из чата" + (message==null ? "":": " + message));
    }

    private void broadcast(final String message) throws InterruptedException {
        queue.addServerMessage(Message.builder()
                .messageType(Message.MessageType.BROADCAST)
                .message(message)
                .build());
    }

    private void connect(final SocketAddress socketAddress) {
        User user = User.builder()
                .socketAddress(socketAddress)
                .state(UserState.CONNECTED)
                .build();
        userMap.put(socketAddress, user);
    }

    private void login(final User user, final String login) {
        final SocketAddress socketAddress = user.getSocketAddress();
        User loggedUser = User.builder()
                .socketAddress(socketAddress)
                .state(UserState.LOGGED)
                .login(login)
                .build();
        userMap.put(socketAddress, loggedUser);
        loginMap.put(login, loggedUser);
    }
}
