package com.fashion.recommendation.recognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class BailianGarmentRecognitionServiceTest {
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
            var service = new BailianGarmentRecognitionService(
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                    "test-key",
                    "vision-test",
                    true,
                    Duration.ofMillis(50),
                    Duration.ofMillis(50));
            var image = new MockMultipartFile("image", "shirt.png", "image/png", new byte[] {1, 2, 3});

            Optional<GarmentRecognitionResult> result = assertTimeout(
                    Duration.ofSeconds(2), () -> service.recognize(image));

            assertEquals(Optional.empty(), result);
        } finally {
            server.stop(0);
        }
    }
}
