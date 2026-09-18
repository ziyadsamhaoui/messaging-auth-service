package com.ziyadsamhaoui.messagingauthservice.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class TestUserServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger callCount = new AtomicInteger();

    public TestUserServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/internal/users", exchange -> {
            callCount.incrementAndGet();
            try (InputStream body = exchange.getRequestBody()) {
                body.readAllBytes();
            }
            if (failing) {
                exchange.sendResponseHeaders(502, -1);
            } else {
                byte[] response = "{\"id\":\"00000000-0000-0000-0000-000000000000\",\"username\":\"stub\"}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, response.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(response);
                }
            }
            exchange.close();
        });
        server.start();
    }

    private volatile boolean failing = false;

    public void failNextCalls() {
        failing = true;
    }

    public void succeed() {
        failing = false;
    }

    public int callCount() {
        return callCount.get();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
