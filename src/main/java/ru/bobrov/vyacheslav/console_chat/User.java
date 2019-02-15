package ru.bobrov.vyacheslav.console_chat;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.net.SocketAddress;

@Value
@Builder
class User {
    @NonNull
    SocketAddress socketAddress;
    @NonNull
    UserState state;
    String login;
}
