package ru.netology;



import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Server {
    private final int port;
    private final ExecutorService threadPool;
    private final Map<String, Map<String, Handler>> handlerMap;
    private ServerSocket serverSocket;

    // Разрешённые пути
    private static final List<String> VALID_PATHS = List.of(
            "/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js",
            "/links.html", "/forms.html", "/classic.html",
            "/events.html", "/events.js"
    );

    public Server(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(64);
        this.handlerMap = new ConcurrentHashMap<>();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);

        while (!serverSocket.isClosed()) {
            Socket socket = serverSocket.accept();
            threadPool.execute(() -> handleConnection(socket));
        }
    }

    private void handleConnection(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            Request request = parseRequest(requestLine);
            System.out.println("Received: " + request);

            if (!VALID_PATHS.contains(request.getPath())) {
                sendNotFound(out);
                return;
            }

            Map<String, Handler> methodHandlers = handlerMap.get(request.getMethod());
            if (methodHandlers == null) {
                sendNotFound(out);
                return;
            }

            Handler handler = methodHandlers.get(request.getPath());
            if (handler == null) {
                sendNotFound(out);
                return;
            }

            handler.handle(request, out);

        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    private ru.netology.Request parseRequest(String requestLine) {
        try {
            return ru.netology.Request.parse(requestLine);
        } catch (Exception e) {
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "";
            return new ru.netology.Request(method, path);
        }
    }

    public void addHandler(String method, String path, Handler handler) {
        handlerMap.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
                .put(path, handler);
    }

    // Вспомогательные методы для ответов

    public void sendOk(BufferedOutputStream out, String mimeType, byte[] content) throws IOException {
        out.write(("HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + content.length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes());
        out.write(content);
        out.flush();
    }

    public void sendOk(BufferedOutputStream out, String mimeType, Path filePath) throws IOException {
        long length = Files.size(filePath);
        out.write(("HTTP/1.1 200 OK\r\n" +
                "Content-Type: " + mimeType + "\r\n" +
                "Content-Length: " + length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    public void sendNotFound(BufferedOutputStream out) throws IOException {
        out.write(("HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n").getBytes());
        out.flush();
    }

    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        threadPool.shutdown();
    }
}




