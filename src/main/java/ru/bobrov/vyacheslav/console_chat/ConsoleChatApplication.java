package ru.bobrov.vyacheslav.console_chat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsoleChatApplication implements CommandLineRunner {
    @Autowired
    private ConnectionsHolder connectionsHolder;

    @Autowired
    private ChatServer chatServer;

    public static void main(String[] args) {
        SpringApplication.run(ConsoleChatApplication.class, args);
    }

    @Override
    public void run(String... args) {
        new Thread(connectionsHolder, "ConnectionHolder").start();
        new Thread(chatServer, "ChatServer").start();
    }
}
