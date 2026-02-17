package ru.netology;

import java.net.Socket;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Server server = new Server();
        while (true) {
           Socket socket = server.serverStart(9999);
            server.connect(socket);
        }
    }
}