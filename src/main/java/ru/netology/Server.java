package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class Server {
    static final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js");

    public Socket serverStart(int port) {
        try (var serverSocket = new ServerSocket(port)) {//запуск сервера

            return serverSocket.accept();//ожидание подключения

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void connect(Socket socket) {
        try (
                socket;
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine(); //запрос
            final var parts = requestLine.split(" ");
            final var path = parts[1];

            if (parts.length != 3 && !validPaths.contains(path)) {
                notFound(out);
            } else {
                getResponse(out, path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void notFound(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 404 Not Found\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    public static String yesFound(String mimeType, long length) {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
    }

    public static void getResponse(BufferedOutputStream out, String path) throws IOException {
        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);
        // special case for classic
        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write(yesFound(mimeType, content.length).getBytes());
            out.write(content);
        } else {
            final var length = Files.size(filePath);
            out.write(yesFound(mimeType, length).getBytes());
            Files.copy(filePath, out);
        }
        out.flush();
    }
}



