package ru.netology;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Server server = new Server(9999);

        // Обработчик для статических файлов
        server.addHandler("GET", "/index.html", (request, out) -> {
            try {
                Path filePath = Path.of(".", "public", "index.html");
                String mimeType = Files.probeContentType(filePath);
                server.sendOk(out, mimeType, filePath);
            } catch (Exception e) {
                try {
                    server.sendNotFound(out);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        server.addHandler("GET", "/spring.svg", (request, out) -> {
            try {
                Path filePath = Path.of(".", "public", "spring.svg");
                String mimeType = Files.probeContentType(filePath);
                server.sendOk(out, mimeType, filePath);
            } catch (Exception e) {
                try {
                    server.sendNotFound(out);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        server.addHandler("GET", "/classic.html", (request, out) -> {
            try {
                Path filePath = Path.of(".", "public", "classic.html");
                String template = Files.readString(filePath);
                String content = template.replace("{time}", LocalDateTime.now().toString());
                byte[] bytes = content.getBytes(UTF_8);
                server.sendOk(out, "text/html", bytes);
            } catch (Exception e) {
                try {
                    server.sendNotFound(out);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        // Запуск сервера
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Server failed to start: " + e.getMessage());
        } finally {
            server.stop();
        }
    }
}

