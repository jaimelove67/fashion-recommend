package com.fashion.recommendation.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.recommendation.ai.*;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ConsultationClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String ANSWER = """
            {"answer":"蓝色衬衫可配米白裤装，让蓝色成为重点。","exampleItemIds":[1],"visuals":[
            {"title":"蓝与米白","pieces":[{"label":"上装","color":"蓝色","hex":"#537DA8"},
            {"label":"下装","color":"米白","hex":"#EEE7D9"}],"explanation":"浅色下装平衡蓝色。"}]}
            """;

    @Test
    void sendsBoundedReferenceContextWithoutPrivateImagesAndParsesStructuredAnswer() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var response = mapper.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", ANSWER);
        server.expect(requestTo("https://model.example/chat"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("configured-text"))
                .andExpect(request -> {
                    var body = new String(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                            .getBodyAsBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    var messages = mapper.readTree(body).path("messages");
                    assertEquals(2, messages.size());
                    assertEquals("system", messages.get(0).path("role").asText());
                    var data = mapper.readTree(messages.get(1).path("content").asText());
                    assertEquals("米白可以吗？", data.path("question").asText());
                    assertEquals("蓝衬衫怎么搭？", data.path("conversation").get(0).path("text").asText());
                    assertEquals(1, data.path("wardrobe").get(0).path("id").asLong());
                    assertFalse(body.contains("private-image"));
                    assertFalse(body.contains("private-object-key"));
                }).andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var client = new ConsultationClient(mapper, factory.getValidator(), configuration(true, "test-key"), builder.build());
            var result = client.answer(new ConsultationRequest("米白可以吗？",
                    List.of(new ConsultationRequest.Turn("user", "蓝衬衫怎么搭？"))),
                    List.of(new WardrobeItem(1L, "蓝色衬衫", "上装", "蓝色", "通勤", "private-image", Instant.now(),
                            "MANUAL", null, "private-object-key")));
            assertEquals(List.of(1L), result.exampleItemIds());
            assertEquals("#537DA8", result.visuals().get(0).pieces().get(0).hex());
        }
        server.verify();
    }

    @Test
    void rejectsMalformedResultsAndUnsafeCssValues() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var client = new ConsultationClient(mapper, factory.getValidator(), configuration(true, "test-key"), RestClient.create());
            assertEquals(1, client.parseAnswer(ANSWER).exampleItemIds().size());
            for (String bad : List.of(ANSWER + "{}", ANSWER.replace("#537DA8", "url(https://evil.example)"),
                    ANSWER.replace("[1]", "[1,1]"), ANSWER.replace("[1]", "[\"1\"]"),
                    ANSWER.replace("[1]", "[1.5]"), ANSWER.replace("[1]", "[null]"),
                    ANSWER.replace("[1]", "[-1]"), ANSWER.replace("\"answer\":", "\"unknown\":"),
                    ANSWER.replace("蓝与米白", "长".repeat(81)), "null", "{}")) {
                assertThrows(Exception.class, () -> client.parseAnswer(bad));
            }
        }
    }

    @Test
    void disabledMissingKeyAndProviderFailureReturnHonestErrors() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (var config : List.of(configuration(false, "test-key"), configuration(true, ""))) {
                var client = new ConsultationClient(mapper, factory.getValidator(), config, RestClient.create());
                var error = assertThrows(ResponseStatusException.class,
                        () -> client.answer(new ConsultationRequest("你好", null), List.of()));
                assertEquals(503, error.getStatusCode().value());
            }
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://model.example/chat")).andRespond(withServerError());
            var client = new ConsultationClient(mapper, factory.getValidator(), configuration(true, "test-key"), builder.build());
            var error = assertThrows(ResponseStatusException.class,
                    () -> client.answer(new ConsultationRequest("你好", null), List.of()));
            assertEquals(502, error.getStatusCode().value());
            assertFalse(error.getReason().contains("test-key"));
            server.verify();
        }
    }

    private static AiModelConfigurationService configuration(boolean enabled, String key) {
        var service = mock(AiModelConfigurationService.class);
        var config = new AiModelRuntimeConfig(AiModelCapability.OUTFIT_RECOMMENDATION, AiModelProvider.DASHSCOPE,
                "configured-text", key, enabled, "https://model.example/chat", null, "ENVIRONMENT", !key.isBlank(), false, false, null);
        when(service.resolve(AiModelCapability.OUTFIT_RECOMMENDATION)).thenReturn(config);
        when(service.isEffectivelyEnabled(config)).thenReturn(enabled);
        return service;
    }
}
