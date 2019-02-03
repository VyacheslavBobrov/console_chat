package ru.bobrov.vyacheslav.console_chat;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsoleChatApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleChatApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

    }
}
