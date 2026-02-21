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

    private Request parseRequest(String requestLine) {
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0] : "";
        String path = parts.length > 1 ? parts[1] : "";
        return new Request(method, path);
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

    /*public void serverStart(int port) {
        threadPool = Executors.newFixedThreadPool(NUMBER_THREADS);
        try (final var serverSocket = new ServerSocket(port)) {//запуск сервера
            while (true) {
                socket = serverSocket.accept();
                System.out.println("\n" + socket);
                threadPool.execute(this::connect);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    public void connect() {
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {
            while (true) {
                final var request = createRequest(in);
                if (!validPaths.contains(request.getPath())) {
                    notFound(out);
                    return;
                }
                var methodHandlers = handlerMap.get(request.getMethod());
                if (methodHandlers == null) {
                    notFound(out);
                    return;
                }

                // Получаем сам обработчик
                Handler handler = methodHandlers.get(request.getPath());
                if (handler == null) {
                    notFound(out);
                    return;
                }

                handler.handle(request, out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createResponse(Request request, BufferedOutputStream out) throws IOException {
        final var filePath = Path.of(".", "public", request.getPath());
        final var mimeType = Files.probeContentType(filePath);

        if (Objects.equals(request.getMethod(), "/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace("{time}", LocalDateTime.now().toString()).getBytes();
            out.write(yesFound(mimeType, content.length).getBytes());
            out.write(content);
            out.flush();

        }
        final var length = Files.size(filePath);
        out.write(yesFound(mimeType, length).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    public Request createRequest(BufferedReader in) throws IOException {
        var requestLine = in.readLine();
        final var parts = requestLine.split(" ");
        return new Request(parts[0], parts[1]);
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

    public void addHandler(String method, String path, Handler handler) {
        if (handlerMap.containsKey(method)) {
            if (!handlerMap.get(method).containsKey(path)) {
                var pathMap = handlerMap.get(method);
                pathMap.put(path, handler);
                handlerMap.put(method, pathMap);
            }
        } else {
            var pathMap = new ConcurrentHashMap<String, Handler>();
            pathMap.put(path, handler);
            handlerMap.put(method, pathMap);
        }
        System.out.println(handlerMap);
    }*/
}



