package ru.bobrov.vyacheslav.console_chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
public class MessageQueue {
    private final BlockingQueue<Message> connectionToServerMessages;
    private final BlockingQueue<Message> serverToConnectionMessages;
    private List<NewMessageListener> listeners = Collections.synchronizedList(new ArrayList<>());

    public MessageQueue(@Value("${queueSize:512}") final int queueSize) {
        connectionToServerMessages = new ArrayBlockingQueue<>(queueSize);
        serverToConnectionMessages = new ArrayBlockingQueue<>(queueSize);
    }

    public void addConnectionMessage(Message message) throws InterruptedException {
        log.trace("В очередь добавлено новое сообщение: " + message);
        connectionToServerMessages.put(message);
    }

    public void addServerMessage(Message message) throws InterruptedException {
        log.trace("В очередь добавлено новое сообщение: " + message);
        serverToConnectionMessages.put(message);
        notifyListeners();
    }

    private void notifyListeners() {
        listeners.forEach(NewMessageListener::onMessageAdded);
    }

    public Message takeConnectionMessage() throws InterruptedException {
        Message message = connectionToServerMessages.take();
        log.trace("Из очереди взято сообщение: " + message);
        return message;
    }

    public Message pollServerMessages() {
        Message message = serverToConnectionMessages.poll();
        if (message!=null)
            log.trace("Из очереди взято сообщение: " + message);
        return message;
    }

    public interface NewMessageListener {
        void onMessageAdded();
    }

    public void addNewMessageListener(NewMessageListener listener) {
        listeners.add(listener);
    }
}
