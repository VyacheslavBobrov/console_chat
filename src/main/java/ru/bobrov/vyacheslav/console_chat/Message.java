package ru.bobrov.vyacheslav.console_chat;

import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.net.SocketAddress;

@Value
@ToString
@Builder
public class Message {
    /**
     * Виды сообщений
     */
    public enum MessageType {
        /**
         * Получено новое соединение
         */
        CONNECTED,
        /**
         * Соединение разорвано клиентом
         */
        DISCONNECTED,
        /**
         * Прочитаны данные из соединения
         */
        READ,
        /**
         * Требуется запись в соединение
         */
        WRITE,
        /**
         * Отсылка сообщения всем подключенным пользователям
         */
        BROADCAST,
        /**
         * Требуется прервать соединение
         */
        KILL
    }

    @NonNull
    MessageType messageType;

    /**
     * Соединение
     */
    SocketAddress socketAddress;
    /**
     * Сообщение для соединения
     */
    String message;
}
