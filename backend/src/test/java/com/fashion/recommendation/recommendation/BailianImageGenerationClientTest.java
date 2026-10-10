package com.fashion.recommendation.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.ai.AiModelCapability;
import com.fashion.recommendation.ai.AiModelConfigurationService;
import com.fashion.recommendation.ai.AiModelProvider;
import com.fashion.recommendation.ai.AiModelRuntimeConfig;
import com.fashion.recommendation.storage.ImageStorage;
import com.fashion.recommendation.storage.StoredImageData;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BailianImageGenerationClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesWanImageSynchronouslyWithoutAsyncHeader() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://aliyun.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ImageStorage storage = mock(ImageStorage.class);
        when(storage.read("small-alpha.png")).thenReturn(new StoredImageData(
                imageBytes(151, 200, BufferedImage.TYPE_INT_ARGB, "png"), "image/png"));
        BailianImageGenerationClient client = new BailianImageGenerationClient(
                builder.build(), objectMapper, new DefaultResourceLoader(), storage,
                "/generate", "/tasks", "secret-key", "wan2.6-image", true,
                "classpath:reference_photo/model-male.png", "classpath:reference_photo/model-female.png",
                "", Duration.ofSeconds(2), Duration.ofMillis(1));

        server.expect(requestTo("http://aliyun.test/generate"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(headerDoesNotExist("X-DashScope-Async"))
                .andExpect(request -> {
                    var body = (org.springframework.mock.http.client.MockClientHttpRequest) request;
                    var payload = objectMapper.readTree(body.getBodyAsString());
                    assertFalse(payload.path("parameters").path("prompt_extend").asBoolean(true));
                    var contents = payload
                            .path("input").path("messages").get(0).path("content");
                    assertEquals(3, contents.size());
                    assertTrue(contents.get(0).path("text").asText().contains("背景必须为纯色浅灰背景"));
                    String uri = contents.get(2).path("image").asText();
                    BufferedImage reference = ImageIO.read(new ByteArrayInputStream(
                            Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))));
                    assertTrue(reference.getWidth() >= 240 && reference.getHeight() >= 240);
                    assertEquals(240, reference.getWidth());
                    assertEquals(318, reference.getHeight());
                    assertFalse(reference.getColorModel().hasAlpha());
                    assertTrue(uri.startsWith("data:image/jpeg;base64,"));
                    assertEquals(0xFFFFFF, reference.getRGB(0, 0) & 0xFFFFFF);
                    var color = new java.awt.Color(reference.getRGB(reference.getWidth() / 2, reference.getHeight() / 2));
                    assertEquals(0x33, color.getRed(), 5);
                    assertEquals(0x66, color.getGreen(), 5);
                    assertEquals(0x99, color.getBlue(), 5);
                })
                .andRespond(withSuccess(
                        "{\"request_id\":\"sync-request\",\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://result.test/sync-look.png\"}]}}]}}",
                        MediaType.APPLICATION_JSON));

        OutfitImageGenerationResult result = client.generate("FEMALE", "通勤", "杭州", 24.0,
                List.of(new WardrobeItem(1L, "衬衫", "上装", "白", "通勤", null,
                        Instant.now(), "MANUAL", null, "small-alpha.png")));

        assertTrue(result.succeeded());
        assertEquals("https://result.test/sync-look.png", result.imageUrl());
        assertEquals("sync-request", result.requestId());
        server.verify();
    }

    @Test
    void sendsPrototypeAndWardrobePromptAndPollsWanTask() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://aliyun.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        ImageStorage imageStorage = mock(ImageStorage.class);
        when(imageStorage.read("wardrobe/1.png")).thenReturn(new StoredImageData(
                imageBytes(300, 400, BufferedImage.TYPE_INT_RGB, "jpeg"), "image/jpeg"));
        BailianImageGenerationClient client = new BailianImageGenerationClient(
                restClient,
                objectMapper,
                new DefaultResourceLoader(),
                imageStorage,
                "/generate",
                "/tasks",
                "secret-key",
                "wan2.6-image",
                true,
                "classpath:reference_photo/model-male.png",
                "classpath:reference_photo/model-female.png",
                "",
                Duration.ofSeconds(2),
                Duration.ofMillis(1));

        server.expect(requestTo("http://aliyun.test/generate"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("\"model\":\"wan2.6-image\"")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("\"enable_interleave\":false")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("data:image/jpeg;base64,")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string(containsString("请以参考图中的人物作为模特原型")))
                .andRespond(withSuccess(
                        "{\"request_id\":\"request-1\",\"output\":{\"task_id\":\"task-1\",\"task_status\":\"PENDING\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://aliyun.test/tasks/task-1"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andRespond(withSuccess(
                        "{\"request_id\":\"request-1\",\"output\":{\"task_id\":\"task-1\",\"task_status\":\"SUCCEEDED\",\"choices\":[{\"message\":{\"content\":[{\"type\":\"image\",\"image\":\"https://result.test/look.png\"}]}}]}}",
                        MediaType.APPLICATION_JSON));

        OutfitImageGenerationResult result = client.generate(
                "FEMALE",
                "通勤",
                "杭州",
                24.0,
                List.of(new WardrobeItem(1L, "雾蓝衬衫", "上装", "雾蓝", "极简", null, Instant.now(), "MANUAL", null, "wardrobe/1.png")));

        assertTrue(result.succeeded());
        assertEquals("https://result.test/look.png", result.imageUrl());
        assertEquals("request-1", result.requestId());
        server.verify();
    }

    @Test
    void doesNotCallProviderWhenImageGenerationIsDisabled() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("http://aliyun.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();
        BailianImageGenerationClient client = new BailianImageGenerationClient(
                restClient,
                objectMapper,
                new DefaultResourceLoader(),
                mock(ImageStorage.class),
                "/generate",
                "/tasks",
                "secret-key",
                "wan2.6-image",
                false,
                "classpath:reference_photo/model-male.png",
                "classpath:reference_photo/model-female.png",
                "",
                Duration.ofSeconds(2),
                Duration.ofMillis(1));

        OutfitImageGenerationResult result = client.generate("MALE", "通勤", "杭州", 24.0, List.of());

        assertEquals("UNAVAILABLE", result.status());
        assertEquals("穿搭效果图生成功能尚未启用，请联系管理员。", result.message());
        server.verify();
    }

    @Test
    void sendsSelectedPersonalPhotoAsTheFirstImageInsteadOfTheDefaultPrototype() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://aliyun.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var client = new BailianImageGenerationClient(builder.build(), objectMapper, new DefaultResourceLoader(),
                mock(ImageStorage.class), "/generate", "/tasks", "secret-key", "wan2.6-image", true,
                "classpath:missing-male.png", "classpath:missing-female.png", "", Duration.ofSeconds(2), Duration.ofMillis(1));
        server.expect(requestTo("http://aliyun.test/generate")).andExpect(request -> {
            var body = (org.springframework.mock.http.client.MockClientHttpRequest) request;
            var payload = objectMapper.readTree(body.getBodyAsString());
            var content = payload.path("input").path("messages").get(0).path("content");
            assertEquals(2, content.size());
            assertTrue(content.get(0).path("text").asText().contains("第一张图片是人物参考"));
            assertTrue(content.get(0).path("text").asText().contains("背景必须为纯色浅灰背景"));
            assertFalse(payload.path("parameters").path("prompt_extend").asBoolean(true));
            String uri = content.get(1).path("image").asText();
            BufferedImage photo = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))));
            assertEquals(320, photo.getWidth());
            assertEquals(480, photo.getHeight());
            var color = new java.awt.Color(photo.getRGB(160, 240));
            assertEquals(0x33, color.getRed(), 5);
            assertEquals(0x66, color.getGreen(), 5);
            assertEquals(0x99, color.getBlue(), 5);
        }).andRespond(withSuccess("{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://images.test/personal.png\"}]}}]}}", MediaType.APPLICATION_JSON));
        var result = client.generate("FEMALE", "通勤", "长沙", 22.0, List.of(),
                new StoredImageData(imageBytes(320, 480, BufferedImage.TYPE_INT_RGB, "png"), "image/png"));
        assertTrue(result.succeeded());
        server.verify();
    }

    @Test
    void skipsCorruptReferenceAndNormalizesInlinePhotoDespiteIncorrectMimeLabel() throws Exception {
        ImageStorage storage = mock(ImageStorage.class);
        when(storage.read("corrupt.png")).thenReturn(new StoredImageData(new byte[] {1, 2, 3}, "image/png"));
        BailianImageGenerationClient client = new BailianImageGenerationClient(
                RestClient.create(), objectMapper, new DefaultResourceLoader(), storage,
                "/generate", "/tasks", "secret-key", "wan2.6-image", true,
                "classpath:reference_photo/model-male.png", "classpath:reference_photo/model-female.png",
                "", Duration.ofSeconds(2), Duration.ofMillis(1));
        String inlinePhoto = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(
                imageBytes(300, 400, BufferedImage.TYPE_INT_ARGB, "png"));

        var request = client.buildRequest("MALE", "通勤", "杭州", 24.0, List.of(
                new WardrobeItem(1L, "白衬衫", "上装", "白", "通勤", null,
                        Instant.now(), "MANUAL", null, "corrupt.png"),
                new WardrobeItem(2L, "黑长裤", "下装", "黑", "通勤", inlinePhoto,
                        Instant.now(), "MANUAL", null, null)), "prototype");

        var contents = request.path("input").path("messages").get(0).path("content");
        assertEquals(3, contents.size());
        assertTrue(contents.get(0).path("text").asText().contains("白衬衫"));
        String uri = contents.get(2).path("image").asText();
        assertTrue(uri.startsWith("data:image/jpeg;base64,"));
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))))) {
            var reader = ImageIO.getImageReaders(input).next();
            try {
                reader.setInput(input);
                assertEquals("JPEG", reader.getFormatName());
                BufferedImage reference = reader.read(0);
                assertEquals(300, reference.getWidth());
                assertEquals(400, reference.getHeight());
                assertFalse(reference.getColorModel().hasAlpha());
            } finally {
                reader.dispose();
            }
        }
    }

    @Test
    void normalizesAnUploadedWebpPersonalPhotoToRgbJpeg() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://aliyun.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BailianImageGenerationClient client = new BailianImageGenerationClient(
                builder.build(), objectMapper, new DefaultResourceLoader(), mock(ImageStorage.class),
                "/generate", "/tasks", "secret-key", "wan2.6-image", true,
                "classpath:reference_photo/model-male.png", "classpath:reference_photo/model-female.png",
                "", Duration.ofSeconds(2), Duration.ofMillis(1));
        byte[] webp = Base64.getDecoder().decode("UklGRiQAAABXRUJQVlA4TBgAAAAv/8A/AAdQs840s/8BAEX6/58i+p/6338=");
        server.expect(requestTo("http://aliyun.test/generate")).andExpect(request -> {
            var body = (org.springframework.mock.http.client.MockClientHttpRequest) request;
            var content = objectMapper.readTree(body.getBodyAsString()).path("input").path("messages").get(0).path("content");
            String uri = content.get(1).path("image").asText();
            assertTrue(uri.startsWith("data:image/jpeg;base64,"));
            var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))));
            assertEquals(256, image.getWidth());
            assertEquals(256, image.getHeight());
            assertFalse(image.getColorModel().hasAlpha());
            assertEquals(0x33, new java.awt.Color(image.getRGB(128, 128)).getRed(), 5);
        }).andRespond(withSuccess("{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://result.test/webp.png\"}]}}]}}", MediaType.APPLICATION_JSON));
        assertTrue(client.generate("MALE", "通勤", "长沙", 22.0, List.of(), new StoredImageData(webp, "image/webp")).succeeded());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reportsTheRuntimeModelForSynchronousAndPolledResults(boolean poll) throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://aliyun.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BailianImageGenerationClient client = new BailianImageGenerationClient(
                builder.build(), objectMapper, new DefaultResourceLoader(), mock(ImageStorage.class),
                "/generate", "/tasks", "environment-key", "environment-model", true,
                "classpath:reference_photo/model-male.png", "classpath:reference_photo/model-female.png",
                "", Duration.ofSeconds(2), Duration.ofMillis(1));
        var config = new AiModelRuntimeConfig(AiModelCapability.DAILY_IMAGE_GENERATION, AiModelProvider.DASHSCOPE,
                "configured-model", "runtime-key", true, "/generate", "/tasks", "DATABASE", true, true, true, null);
        var configuration = mock(AiModelConfigurationService.class);
        when(configuration.resolve(AiModelCapability.DAILY_IMAGE_GENERATION)).thenReturn(config);
        when(configuration.isEffectivelyEnabled(config)).thenReturn(true);
        ReflectionTestUtils.setField(client, "modelConfigurationService", configuration);
        String imageResponse = "{\"output\":{\"task_status\":\"SUCCEEDED\",\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://result.test/runtime.png\"}]}}]}}";
        server.expect(requestTo("http://aliyun.test/generate"))
                .andExpect(header("Authorization", "Bearer runtime-key"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string(containsString("\"model\":\"configured-model\"")))
                .andRespond(withSuccess(poll ? "{\"request_id\":\"runtime-request\",\"output\":{\"task_id\":\"runtime-task\"}}" : imageResponse, MediaType.APPLICATION_JSON));
        if (poll) server.expect(requestTo("http://aliyun.test/tasks/runtime-task"))
                .andExpect(header("Authorization", "Bearer runtime-key"))
                .andRespond(withSuccess(imageResponse, MediaType.APPLICATION_JSON));
        var result = client.generate("MALE", "通勤", "长沙", 22.0, List.of());
        assertTrue(result.succeeded());
        assertEquals("configured-model", result.model());
        server.verify();
    }

    private static byte[] imageBytes(int width, int height, int type, String format) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BufferedImage image = new BufferedImage(width, height, type);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new java.awt.Color(0x336699));
            graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
