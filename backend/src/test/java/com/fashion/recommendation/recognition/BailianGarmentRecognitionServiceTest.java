package com.fashion.recommendation.recognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BailianGarmentRecognitionServiceTest {
    @Test
    void sendsUserRoleWithImageAndParsesRecognition() throws Exception {
        var mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            var request = mapper.readTree(exchange.getRequestBody());
            var user = request.path("messages").path(1);
            boolean valid = "user".equals(user.path("role").asText())
                    && user.path("content").path(1).path("image_url").path("url").asText().startsWith("data:image/png;base64,");
            String body = valid
                    ? "{\"choices\":[{\"message\":{\"content\":\"{\\\"name\\\":\\\"白衬衫\\\",\\\"category\\\":\\\"上装\\\",\\\"color\\\":\\\"白\\\",\\\"style\\\":\\\"通勤\\\"}\"}}]}"
                    : "{\"error\":{\"code\":\"invalid_request\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(valid ? 200 : 400, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var service = new BailianGarmentRecognitionService(RestClient.create(), mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "test-key", "vision-test", true);
            var image = new MockMultipartFile("image", "shirt.png", "image/png", new byte[] {1, 2, 3});
            assertEquals(Optional.of(new GarmentRecognitionResult("白衬衫", "上装", "白", "通勤")), service.recognize(image));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void offersTheDressCategoryToTheVisionProvider() throws Exception {
        var mapper = new ObjectMapper();
        var requestBody = new java.util.concurrent.atomic.AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var content = mapper.createObjectNode().put("name", "蓝色连衣裙").put("category", "连体装")
                    .put("color", "蓝色").put("style", "日常").toString();
            var response = mapper.createObjectNode();
            response.putArray("choices").addObject().putObject("message").put("content", content);
            byte[] bytes = mapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var service = new BailianGarmentRecognitionService(RestClient.create(), mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "test-key", "vision-test", true);
            var image = new MockMultipartFile("image", "dress.png", "image/png", new byte[] {1, 2, 3});
            assertEquals(Optional.of(new GarmentRecognitionResult("蓝色连衣裙", "连体装", "蓝色", "日常")),
                    service.recognize(image));
            var messages = mapper.readTree(requestBody.get()).path("messages");
            assertTrue(messages.path(0).path("content").asText().contains("连体装"));
            assertTrue(messages.path(1).path("content").path(0).path("text").asText().contains("连体装"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsEmptyWhenVisionProviderExceedsReadTimeout() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat", exchange -> {
            try {
                Thread.sleep(500);
                exchange.sendResponseHeaders(200, 0);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofMillis(50));
            requestFactory.setReadTimeout(Duration.ofMillis(50));
            var service = new BailianGarmentRecognitionService(
                    RestClient.builder().requestFactory(requestFactory).build(),
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                    "test-key",
                    "vision-test",
                    true);
            var image = new MockMultipartFile("image", "shirt.png", "image/png", new byte[] {1, 2, 3});

            Optional<GarmentRecognitionResult> result = assertTimeout(
                    Duration.ofSeconds(2), () -> service.recognize(image));

            assertEquals(Optional.empty(), result);
        } finally {
            server.stop(0);
        }
    }
}
