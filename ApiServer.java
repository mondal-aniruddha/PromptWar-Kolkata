package com.mindmate.api;

import com.mindmate.model.ChatRequest;
import com.mindmate.model.JournalEntry;
import com.mindmate.service.WellnessService;
import com.mindmate.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class ApiServer {
    private final HttpServer server;
    private final WellnessService wellnessService = new WellnessService();

    public ApiServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", this::handleHealth);
        server.createContext("/api/entries", this::handleEntries);
        server.createContext("/api/insights", this::handleInsights);
        server.createContext("/api/chat", this::handleChat);
        server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public void start() {
        server.start();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        send(exchange, 200, Map.of("status", "ok", "time", Instant.now().toString()));
    }

    private void handleEntries(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }

        if ("GET".equals(exchange.getRequestMethod())) {
            send(exchange, 200, wellnessService.getEntries());
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            JournalEntry entry = Json.fromJson(readBody(exchange), JournalEntry.class);
            JournalEntry saved = wellnessService.addEntry(entry);
            send(exchange, 201, saved);
            return;
        }

        send(exchange, 405, Map.of("error", "Method not allowed"));
    }

    private void handleInsights(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }

        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        send(exchange, 200, wellnessService.buildDashboard());
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "");
            return;
        }

        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        ChatRequest request = Json.fromJson(readBody(exchange), ChatRequest.class);
        send(exchange, 200, wellnessService.replyTo(request));
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void send(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        addCors(exchange);
        String response = payload instanceof String text ? text : Json.toJson(payload);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private void addCors(HttpExchange exchange) {
        List<String> requestedHeaders = exchange.getRequestHeaders().get("Access-Control-Request-Headers");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers",
                requestedHeaders == null ? "Content-Type" : String.join(",", requestedHeaders));
    }
}
