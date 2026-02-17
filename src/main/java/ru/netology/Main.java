package ru.netology;

import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        Server server = new Server();
        while (true) {
           Socket socket = server.serverStart(9998);
            server.connect(socket);
        }

    }
}