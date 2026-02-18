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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    static final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png",
            "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js");
    final int NUMBER_THREADS = 64;
    private Socket socket;
    private final ExecutorService threadPool;
    private final ConcurrentHashMap<String, Map<String, Handler>> handlerMap;

    public Server() {
        threadPool = Executors.newFixedThreadPool(NUMBER_THREADS);
        handlerMap = new ConcurrentHashMap<>();
    }

    public void serverStart(int port) {
        try (final var serverSocket = new ServerSocket(port)) {//запуск сервера
            //return serverSocket.accept(); //ожидание подключения
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
                Request request = createRequest(in, out);
                Handler handler = handlerMap.get(request.getMethod()).get(request.getPath());
                System.out.println("handler: " + handler);

                final var path = request.getPath();
                if (!validPaths.contains(path)) {
                    notFound(out);
                    return;
                }
                createResponse(request, out);
                System.out.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createResponse(Request request, BufferedOutputStream out) throws IOException {
        final var filePath = Path.of(".", "public", request.getPath());
        final var mimeType = Files.probeContentType(filePath);

        if (request.getPath().equals("/classic.html")) {
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

    public Request createRequest(BufferedReader in, BufferedOutputStream out) throws IOException {
        var requestLine = "";
        do {
            requestLine = in.readLine();
        } while (requestLine == null);
        System.out.println("\n" + requestLine);
        final var parts = requestLine.split(" ");
        if (parts.length != 3) {
            out.write(("Не корректный запрос").getBytes());
            System.out.println("Введён некорректный запрос");
            socket.close();
        }
        String heading;
        Map<String, String> headers = new HashMap<>();
        while (!(heading = in.readLine()).equals("")) {
            var indexOf = heading.indexOf(":");
            var nameHeader = heading.substring(0, indexOf);
            var valueHeader = heading.substring(indexOf + 2);
            headers.put(nameHeader, valueHeader);
        }
        Request request = new Request(parts[0], parts[1], headers, socket.getInputStream());
        System.out.println("request: " + request);
        out.flush();
        return request;
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
            handlerMap.get(method).put(path, handler);
        } else {
            handlerMap.put(method, new ConcurrentHashMap<>(Map.of(path, handler)));
        }
        System.out.println(handlerMap);
    }
}



