package ru.netology;

import com.sun.net.httpserver.Request;

import java.io.BufferedOutputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Server server = new Server();

        server.addHandler("GET", "/spring.svg", new Handler() {
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {
                    server.createResponse((ru.netology.Request) request, responseStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        server.addHandler("GET", "/resources.html", new Handler() {
            public void handle(Request request, BufferedOutputStream responseStream) {
                try {
                    server.createResponse((ru.netology.Request) request, responseStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        server.serverStart(9999);

    }
    }
