package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fashion.recommendation.ai.AiModelCapability;
import com.fashion.recommendation.ai.AiModelConfigurationService;
import com.fashion.recommendation.ai.AiModelProvider;
import com.fashion.recommendation.ai.AiModelRuntimeConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TechniqueGenerationClientTest {
    private static final Instant NOW = Instant.parse("2026-10-16T09:00:00Z");
    private static final String FIRST_QUOTE = "Keep the same jacket and compare two shirts in the same light.";
    private static final String SECOND_QUOTE = "A visible collar edge can connect the inner layer to the jacket.";
    private static final String THIRD_QUOTE = "Choose an inner shirt that remains comfortable when you raise your arms.";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private AiModelConfigurationService models;
    private TechniqueGenerationClient client;

    @BeforeEach void setup() {
        models = mock(AiModelConfigurationService.class);
        when(models.resolve(AiModelCapability.OUTFIT_RECOMMENDATION)).thenReturn(config(true, "test-key"));
        when(models.isEffectivelyEnabled(any())).thenReturn(true);
        client = new TechniqueGenerationClient(null, mapper, models);
    }

    @Test void sourceMetadataComesFromCollectedArticlesAndEvidenceRemainsPrivate() throws Exception {
        OutfitTechnique value = client.validateDraft(draft(), articles(), List.of(), "test-model", NOW);
        assertEquals("weekly-ai", value.generationMode());
        assertEquals("作者甲", value.sources().get(0).author());
        assertNull(value.sources().get(1).publishedAt());
        assertTrue(value.sources().get(0).scope().contains("未人工核读"));
        assertEquals(2, value.sources().size());
        assertEquals(3, value.sourceNotes().size());
        String publicJson = mapper.writeValueAsString(value);
        assertFalse(publicJson.contains("evidence"));
        assertFalse(publicJson.contains(FIRST_QUOTE));
        assertFalse(publicJson.contains("article body"));
        assertEquals(NOW, value.publishedAt());
    }

    @Test void inventedEvidenceAndCrossSourceEvidenceAreRejected() {
        ObjectNode invented = draft();
        ((ObjectNode) invented.path("sourceNotes").get(0)).put("evidence", "This statement does not appear in either article text.");
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(invented, articles(), List.of(), "m", NOW));
        ObjectNode wrongSource = draft();
        ((ObjectNode) wrongSource.path("sourceNotes").get(0)).put("sourceId", "b");
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(wrongSource, articles(), List.of(), "m", NOW));
    }

    @Test void oneSourceDuplicateEvidenceAndDuplicateTitlesAreRejected() {
        ObjectNode single = draft();
        for (var note : single.path("sourceNotes")) ((ObjectNode) note).put("sourceId", "a");
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(single, articles(), List.of(), "m", NOW));
        ObjectNode repeated = draft();
        ((ObjectNode) repeated.path("sourceNotes").get(1)).put("evidence", FIRST_QUOTE);
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(repeated, articles(), List.of(), "m", NOW));
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(draft(), articles(),
                List.of("领口边缘不清楚时，固定外套只比较内搭！"), "m", NOW));
    }

    @Test void verbatimOtherPassageCannotMasqueradeAsParaphrase() {
        List<TechniqueArticle> original = articles();
        String copied = "固定相同外套与鞋子再比较内搭领口的视觉变化这样才能观察单独因素的影响。";
        TechniqueArticle first = original.get(0);
        List<TechniqueArticle> withCopyableBody = List.of(new TechniqueArticle(first.id(), first.title(), first.url(),
                first.author(), first.platform(), first.publishedAt(), first.fetchedAt(), first.text() + copied), original.get(1));
        ObjectNode value = draft();
        ((ObjectNode) value.path("sourceNotes").get(0)).put("point", copied);
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(value, withCopyableBody, List.of(), "m", NOW));
    }

    @Test void fullArticleTailIsSentToBothCallsAndOversizedArticlesAreRejectedRatherThanClipped() {
        List<TechniqueArticle> original = articles();
        TechniqueArticle first = original.get(0);
        String tail = "QUALIFIER_AFTER_10000: this applies only to a relaxed jacket in mild weather.";
        List<TechniqueArticle> complete = List.of(new TechniqueArticle(first.id(), first.title(), first.url(),
                first.author(), first.platform(), first.publishedAt(), first.fetchedAt(), first.text() + "x".repeat(11000) + tail), original.get(1));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (ObjectNode response : List.of(draft(), review(90))) {
            server.expect(requestTo("https://model.example/chat/completions"))
                    .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString(tail)))
                    .andRespond(withSuccess(envelope(response, "test-id", "stop"), MediaType.APPLICATION_JSON));
        }
        new TechniqueGenerationClient(builder.build(), mapper, models).generate(complete, List.of());
        server.verify();
        RestClient http = mock(RestClient.class);
        List<TechniqueArticle> tooLong = List.of(new TechniqueArticle(first.id(), first.title(), first.url(),
                first.author(), first.platform(), first.publishedAt(), first.fetchedAt(), first.text() + "x".repeat(24000)), original.get(1));
        assertThrows(IllegalArgumentException.class, () -> new TechniqueGenerationClient(http, mapper, models).generate(tooLong, List.of()));
        verifyNoInteractions(http);
    }

    @Test void strictSchemaRejectsMissingStepsInjectedMetadataAndHtml() {
        ObjectNode extra = draft();
        extra.put("sources", "https://attacker.example");
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(extra, articles(), List.of(), "m", NOW));
        ObjectNode shortSteps = draft();
        shortSteps.putArray("steps").add("仅提供一条步骤而且不够长");
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(shortSteps, articles(), List.of(), "m", NOW));
        ObjectNode html = draft();
        html.put("title", "<script>伪造一个足够长度的标题</script>");
        assertThrows(IllegalArgumentException.class, () -> client.validateDraft(html, articles(), List.of(), "m", NOW));
    }

    @Test void everyReviewFlagAndScoreMustPassWithNoIssues() {
        assertEquals(85, client.validateReview(review(85)));
        assertThrows(IllegalArgumentException.class, () -> client.validateReview(review(84)));
        assertThrows(IllegalArgumentException.class, () -> client.validateReview(review(101)));
        for (String flag : List.of("approved", "grounded", "actionable", "novel", "limitationsClear")) {
            ObjectNode rejected = review(95).put(flag, false);
            assertThrows(IllegalArgumentException.class, () -> client.validateReview(rejected));
        }
        ObjectNode withIssue = review(95);
        withIssue.putArray("issues").add("第二篇不支持该做法");
        assertThrows(IllegalArgumentException.class, () -> client.validateReview(withIssue));
    }

    @Test void disabledAndMissingCredentialsBlockHttpEntirely() {
        RestClient http = mock(RestClient.class);
        TechniqueGenerationClient disabled = new TechniqueGenerationClient(http, mapper, models);
        when(models.isEffectivelyEnabled(any())).thenReturn(false);
        assertFalse(disabled.available());
        assertThrows(IllegalStateException.class, () -> disabled.generate(articles(), List.of()));
        when(models.isEffectivelyEnabled(any())).thenReturn(true);
        when(models.resolve(any())).thenReturn(config(true, ""));
        assertFalse(disabled.available());
        assertThrows(IllegalStateException.class, () -> disabled.generate(articles(), List.of()));
        verifyNoInteractions(http);
    }

    @Test void successfulGenerationMakesExactlyTwoBoundedCallsAndKeepsBothAuditIds() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/chat/completions"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.max_tokens").value(3600))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("配色不顺")))
                .andRespond(withSuccess(envelope(draft(), "draft-1", "stop"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://model.example/chat/completions"))
                .andExpect(jsonPath("$.max_tokens").value(800))
                .andRespond(withSuccess(envelope(review(92), "review-1", "stop"), MediaType.APPLICATION_JSON));
        TechniqueGenerationResult result = new TechniqueGenerationClient(builder.build(), mapper, models).generate(articles(), List.of());
        assertEquals(92, result.qualityScore());
        assertEquals("draft-1/review-1", result.providerCallId());
        assertNotNull(result.technique());
        server.verify();
    }

    @Test void truncatedOrRejectedDraftStopsBeforeSecondCall() throws Exception {
        for (String finish : List.of("length", "content_filter", "tool_calls")) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("https://model.example/chat/completions"))
                    .andRespond(withSuccess(envelope(draft(), "draft-1", finish), MediaType.APPLICATION_JSON));
            assertThrows(IllegalArgumentException.class, () -> new TechniqueGenerationClient(builder.build(), mapper, models)
                    .generate(articles(), List.of()));
            server.verify();
        }
    }

    @Test void providerErrorsAreClassifiedAsUnavailableWithoutLeakingTheirBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://model.example/chat/completions"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .body("private-provider-key-details"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new TechniqueGenerationClient(builder.build(), mapper, models).generate(articles(), List.of()));
        assertEquals("technique-model-request-failed", failure.getMessage());
        assertNull(failure.getCause());
        server.verify();
    }

    @Test void practicalTutorialsGetContextBudgetBeforeLongProfilesAndShopping() {
        TechniqueArticle first = articles().get(0), second = articles().get(1);
        List<TechniqueArticle> candidates = List.of(
                new TechniqueArticle("shopping", "How To Shop On A Budget", "https://author-a.example/buy", null, "Publisher", null,
                        NOW, "DO_NOT_SPEND_CONTEXT_ON_SHOPPING ".repeat(600)),
                new TechniqueArticle("profile", "A cultural homage", "https://author-a.example/profile", null, "Publisher", null,
                        NOW, "LONG_CULTURAL_PROFILE ".repeat(1000)),
                new TechniqueArticle(first.id(), "How To Think About Color Temperature", first.url(), first.author(), first.platform(),
                        first.publishedAt(), NOW, first.text() + "a".repeat(16000)),
                new TechniqueArticle(second.id(), "Spring/Summer Sport Coat Guide", second.url(), second.author(), second.platform(),
                        second.publishedAt(), NOW, second.text() + "b".repeat(16000)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        for (ObjectNode response : List.of(draft(), review(90))) {
            server.expect(requestTo("https://model.example/chat/completions"))
                    .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("Color Temperature")))
                    .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString("Sport Coat Guide")))
                    .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DO_NOT_SPEND_CONTEXT_ON_SHOPPING"))))
                    .andRespond(withSuccess(envelope(response, "test-id", "stop"), MediaType.APPLICATION_JSON));
        }
        new TechniqueGenerationClient(builder.build(), mapper, models).generate(candidates, List.of());
        server.verify();
    }

    private AiModelRuntimeConfig config(boolean enabled, String key) {
        return new AiModelRuntimeConfig(AiModelCapability.OUTFIT_RECOMMENDATION, AiModelProvider.DASHSCOPE,
                "test-model", key, enabled, "https://model.example/chat/completions", null,
                "ENVIRONMENT", !key.isBlank(), false, false, null);
    }

    private List<TechniqueArticle> articles() {
        return List.of(
                new TechniqueArticle("a", "Collar comparisons", "https://author-a.example/collar", "作者甲", "作者甲博客",
                        "2021-04-20", NOW, FIRST_QUOTE + " " + SECOND_QUOTE + " article body supporting context. ".repeat(30)),
                new TechniqueArticle("b", "Comfortable inner layers", "https://author-b.example/layers", null, "作者乙博客",
                        null, NOW, THIRD_QUOTE + " article body supporting context. ".repeat(30)));
    }

    private ObjectNode draft() {
        ObjectNode value = mapper.createObjectNode();
        value.put("category", "叠穿");
        value.put("title", "领口边缘不清楚时固定外套只比较内搭");
        value.put("summary", "把外套固定下来，只比较两种内搭领口在同样光线下的边缘，先判断自己喜欢的层次，再检查活动时是否舒适。");
        var notes = value.putArray("sourceNotes");
        notes.addObject().put("sourceId", "a").put("point", "第一位作者建议固定外套，在相同光线下比较两件衬衫，避免同时调整多件衣物后无法判断效果。").put("evidence", FIRST_QUOTE);
        notes.addObject().put("sourceId", "a").put("point", "第一位作者还讨论了内搭与外套之间可见的领口边缘，指出边缘可以作为衔接层次的一个观察点。").put("evidence", SECOND_QUOTE);
        notes.addObject().put("sourceId", "b").put("point", "第二位作者把活动舒适度作为内搭选择的条件，建议通过抬手检查衣物是否影响正常的肩背活动。").put("evidence", THIRD_QUOTE);
        value.put("synthesisNote", "两篇文章分别提供视觉比较和舒适度检查，本文把它们整理成先固定变量、再抬手检查的练习；这不是两位作者共同完成的实验，也不代表所有领口都必须可见。");
        value.putArray("steps").add("固定同一件外套和下装，在相同光线与站姿下观察领口目前的边缘。").add("只换已有的另一件内搭，比较自己希望保留的领口高度与可见范围。").add("抬手再坐下，检查肩背是否紧绷，记录哪一种内搭更符合自己的目标。");
        value.put("reason", "固定外套与光线后，内搭领口造成的变化更容易单独观察。领口是否清楚属于个人审美选择，抬手时的舒适度则决定这套搭配能否满足当天活动，两项需要分别检查。");
        value.putObject("example").put("before", "假设同一件外套配圆领内搭时，自己觉得领口边缘不够清楚，但没有活动不适。").put("after", "保留外套与下装，只换一件已有的不同领高内搭，对比层次后再抬手确认舒适度。");
        value.put("suitable", "适合已经有外套与多件内搭，希望比较领口层次而不增加购买的人。");
        value.put("limit", "原文讨论的是作者自己的搭配方法，不能证明某种领口普遍更好；天气、活动与个人覆盖需求应优先，感觉拥挤时可以减少层数。");
        value.put("check", "这次是否只改变了内搭，领口变化是否符合自己的目标，坐下或抬手后是否仍然舒适？");
        return value;
    }

    private ObjectNode review(int score) {
        ObjectNode result = mapper.createObjectNode();
        for (String key : List.of("approved", "grounded", "actionable", "novel", "limitationsClear")) result.put(key, true);
        result.put("score", score);
        result.putArray("issues");
        return result;
    }

    private String envelope(ObjectNode content, String id, String finish) {
        ObjectNode result = mapper.createObjectNode();
        result.put("id", id);
        ObjectNode choice = result.putArray("choices").addObject();
        choice.put("finish_reason", finish);
        choice.putObject("message").put("content", content.toString());
        return result.toString();
    }
}
