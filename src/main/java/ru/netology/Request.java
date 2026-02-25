package ru.netology;

import org.apache.hc.core5.net.URIBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> queryParams;
    private final Map<String, String> formParams;
    private final Map<String, String> multipartParams;
    private final Map<String, byte[]> fileParams;

    public Request(String method, String path) {
        this.method = method;
        this.path = path;
        this.queryParams = new HashMap<>();
        this.formParams = new HashMap<>();
        this.multipartParams = new HashMap<>();
        this.fileParams = new HashMap<>();
    }

    public Request(String method, String path, Map<String, String> queryParams, Map<String, String> formParams, Map<String, String> multipartParams, Map<String, byte[]> fileParams) {
        this.method = method;
        this.path = path;
        this.queryParams = queryParams;
        this.formParams = formParams;
        this.multipartParams = multipartParams;
        this.fileParams = fileParams;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getFormParams() {
        return formParams;
    }

    public String getFormParam(String name) {
        return formParams.get(name);
    }

    public Map<String, String> getMultipartParams() {
        return multipartParams;
    }

    public String getMultipartParam(String name) {
        return multipartParams.get(name);
    }

    public Map<String, byte[]> getFileParams() {
        return fileParams;
    }

    public byte[] getFileParam(String name) {
        return fileParams.get(name);
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    public static Request parse(String requestLine) throws URISyntaxException {
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid request line: " + requestLine);
        }
        
        String method = parts[0];
        URI uri = new URI(parts[1]);
        String path = uri.getPath();
        
        Map<String, String> queryParams = new HashMap<>();
        if (uri.getQuery() != null) {
            String[] params = uri.getQuery().split("&");
            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], keyValue[1]);
                } else {
                    queryParams.put(keyValue[0], "");
                }
            }
        }
        
        return new Request(method, path, queryParams, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @Override
    public String toString() {
        return "Request{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", queryParams=" + queryParams +
                ", formParams=" + formParams +
                ", multipartParams=" + multipartParams +
                ", fileParams=" + fileParams +
                '}';
    }
}