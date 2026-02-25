package ru.netology;



import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
//Конструктор Server(int port):
//
//Инициализирует сервер на указанном порту
//Создает пул из 64 потоков
//Инициализирует карту для хранения обработчиков
    public Server(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(64);
        this.handlerMap = new ConcurrentHashMap<>();
    }
//start():
//
//Создает ServerSocket
//Входит в бесконечный цикл приема подключений
//Для каждого подключения запускает обработку в отдельном потоке
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server started on port " + port);

        while (!serverSocket.isClosed()) {
            Socket socket = serverSocket.accept();
            threadPool.execute(() -> handleConnection(socket));
        }
    }
//handleConnection(Socket socket):
//
//Создает BufferedReader и BufferedOutputStream
//Читает request line
//Парсит запрос с помощью parseRequest()
//Проверяет валидность пути
//Находит и вызывает соответствующий обработчик
    private void handleConnection(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            Request request = parseRequest(requestLine, in);
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
//parseRequest(String requestLine, BufferedReader in):
//
//Основной метод парсинга запроса
//Читает заголовки для получения Content-Type и Content-Length
//Обрабатывает два типа тел:
//
//application/x-www-form-urlencoded: разбирает &-параметры
//multipart/form-data: парсит по boundary, извлекает файлы и поля
//Возвращает Request с заполненными параметрами
    private ru.netology.Request parseRequest(String requestLine, BufferedReader in) throws IOException {
        try {
            ru.netology.Request request = ru.netology.Request.parse(requestLine);
            
            // Читаем заголовки
            String line;
            String contentType = null;
            String boundary = null;
            int contentLength = 0;
            
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-type:")) {
                    contentType = line.substring(13).trim();
                    if (contentType.startsWith("multipart/form-data")) {
                        int boundaryIndex = contentType.indexOf("boundary=");
                        if (boundaryIndex != -1) {
                            boundary = contentType.substring(boundaryIndex + 9);
                        }
                    }
                } else if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            
            // Обрабатываем тело запроса, если это POST и есть данные
            if ("POST".equals(request.getMethod()) && contentLength > 0) {
                if ("application/x-www-form-urlencoded".equals(contentType)) {
                    char[] bodyChars = new char[contentLength];
                    in.read(bodyChars, 0, contentLength);
                    String body = new String(bodyChars);
                    
                    Map<String, String> formParams = new HashMap<>();
                    String[] params = body.split("&");
                    for (String param : params) {
                        String[] keyValue = param.split("=", 2);
                        if (keyValue.length == 2) {
                            formParams.put(keyValue[0], keyValue[1]);
                        } else {
                            formParams.put(keyValue[0], "");
                        }
                    }
                    
                    return new ru.netology.Request(request.getMethod(), request.getPath(), request.getQueryParams(), formParams, new HashMap<>(), new HashMap<>());
                } else if (contentType != null && contentType.startsWith("multipart/form-data") && boundary != null) {
                    Map<String, String> multipartParams = new HashMap<>();
                    Map<String, byte[]> fileParams = new HashMap<>();
                    
                    // Читаем тело multipart запроса
                    StringBuilder bodyBuilder = new StringBuilder();
                    int totalRead = 0;
                    char[] buffer = new char[1024];
                    
                    while (totalRead < contentLength) {
                        int read = in.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead));
                        if (read == -1) break;
                        bodyBuilder.append(buffer, 0, read);
                        totalRead += read;
                    }
                    
                    String body = bodyBuilder.toString();
                    // Удаляем начальную и конечную границы
                    body = body.replaceFirst("^--" + java.util.regex.Pattern.quote(boundary) + "\r\n", "");
                    body = body.replaceFirst("--" + java.util.regex.Pattern.quote(boundary) + "--$", "");
                    String[] parts = body.split("--" + java.util.regex.Pattern.quote(boundary));
                    
                    for (String part : parts) {
                        if (part.trim().isEmpty() || part.trim().equals("--")) continue;
                        
                        int headerEnd = part.indexOf("\r\n\r\n");
                        if (headerEnd == -1) continue;
                        
                        String headers = part.substring(0, headerEnd);
                        String content = part.substring(headerEnd + 4); // +4 для \r\n\r\n
                        
                        // Удаляем последний \r\n
                        if (content.endsWith("\r\n")) {
                            content = content.substring(0, content.length() - 2);
                        }
                        
                        // Проверяем, является ли часть файлом
                        if (headers.contains("filename=\"") || headers.contains("filename*=utf-8''")) {
                            // Извлекаем имя параметра
                            int nameStart = headers.indexOf("name=\"");
                            if (nameStart != -1) {
                                nameStart += 6; // длина "name=\""
                                int nameEnd = headers.indexOf("\"", nameStart);
                                if (nameEnd != -1) {
                                    String paramName = headers.substring(nameStart, nameEnd);
                                    fileParams.put(paramName, content.getBytes(UTF_8));
                                }
                            }
                        } else {
                            // Простое текстовое поле
                            int nameStart = headers.indexOf("name=\"");
                            if (nameStart != -1) {
                                nameStart += 6;
                                int nameEnd = headers.indexOf("\"", nameStart);
                                if (nameEnd != -1) {
                                    String paramName = headers.substring(nameStart, nameEnd);
                                    multipartParams.put(paramName, content);
                                }
                            }
                        }
                    }
                    
                    return new ru.netology.Request(request.getMethod(), request.getPath(), request.getQueryParams(), new HashMap<>(), multipartParams, fileParams);
                }
            }
            
            return new ru.netology.Request(request.getMethod(), request.getPath(), request.getQueryParams(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        } catch (Exception e) {
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "";
            return new ru.netology.Request(method, path);
        }
    }
//addHandler(String method, String path, Handler handler):
//
//Регистрирует обработчик для метода и пути
//Использует вложенные ConcurrentHashMap
    public void addHandler(String method, String path, Handler handler) {
        handlerMap.computeIfAbsent(method, k -> new ConcurrentHashMap<>())
                .put(path, handler);
    }

//Методы отправки ответов:
//
//sendOk(): формирует 200 OK ответ с содержимым
//sendNotFound(): формирует 404 Not Found ответ
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
//stop():
//
//Корректно завершает работу сервера
//Закрывает ServerSocket и пул потоков
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




