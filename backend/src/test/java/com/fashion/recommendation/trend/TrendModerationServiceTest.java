package com.fashion.recommendation.trend;

import com.fashion.recommendation.admin.AdminAuditRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrendModerationServiceTest {
    private final TrendRepository repository = mock(TrendRepository.class);
    private final TrendModerationClient client = mock(TrendModerationClient.class);
    private final AdminAuditRepository audit = mock(AdminAuditRepository.class);
    private final TrendModerationService service = new TrendModerationService(repository, client, audit, 10);

    @Test
    void successfulAiReviewOnlyMovesContentToHumanQueue() {
        TrendModerationItem pending = moderationItem(TrendModerationStatus.PENDING_AI);
        when(client.enabled()).thenReturn(true);
        when(repository.pendingAi(10)).thenReturn(List.of(pending));
        when(client.review(any())).thenReturn(
                new TrendAiReviewResult("PASS", "LOW", "相关", "qwen-plus", "call-1", "trend-moderation-v1"));
        when(repository.markAiReviewed(eq(pending.id()), any(TrendAiReviewResult.class), any(Instant.class)))
                .thenReturn(true);
        when(repository.countByModerationStatus(any())).thenReturn(0L);

        TrendModerationRun run = service.processPending(10);

        assertEquals(1, run.reviewed());
        assertEquals(0, run.failed());
        verify(repository).markAiReviewed(eq(pending.id()), any(TrendAiReviewResult.class), any(Instant.class));
        verify(repository, never()).finalizeHuman(any(), any(), any(), any(), any());
    }

    @Test
    void providerFailureIsIsolatedAndCannotPublishContent() {
        TrendModerationItem pending = moderationItem(TrendModerationStatus.PENDING_AI);
        when(client.enabled()).thenReturn(true);
        when(repository.pendingAi(10)).thenReturn(List.of(pending));
        when(client.review(any())).thenThrow(new TrendModerationException(
                TrendModerationFailureReason.RESPONSE_INVALID, "invalid"));
        when(repository.markAiFailed(eq(pending.id()), eq(TrendModerationFailureReason.RESPONSE_INVALID), any(Instant.class)))
                .thenReturn(true);
        when(repository.countByModerationStatus(any())).thenReturn(0L);

        TrendModerationRun run = service.processPending(10);

        assertEquals(0, run.reviewed());
        assertEquals(1, run.failed());
        verify(repository).markAiFailed(
                eq(pending.id()), eq(TrendModerationFailureReason.RESPONSE_INVALID), any(Instant.class));
        verify(repository, never()).markAiReviewed(any(), any(), any());
    }

    @Test
    void humanApprovalIsTheOnlyPublishingTransition() {
        TrendModerationItem before = moderationItem(TrendModerationStatus.PENDING_HUMAN);
        TrendModerationItem after = moderationItem(TrendModerationStatus.APPROVED);
        when(repository.findModeration(before.id())).thenReturn(java.util.Optional.of(before), java.util.Optional.of(after));
        when(repository.finalizeHuman(eq(before.id()), eq(TrendModerationStatus.APPROVED), eq("admin"), any(Instant.class), eq(null)))
                .thenReturn(true);

        TrendModerationItem result = service.finalizeHuman("admin", before.id(), "APPROVED", null);

        assertEquals(TrendModerationStatus.APPROVED, result.moderationStatus());
        verify(repository).finalizeHuman(
                eq(before.id()), eq(TrendModerationStatus.APPROVED), eq("admin"), any(Instant.class), eq(null));
        verify(audit).append(eq("admin"), eq("TREND_HUMAN_REVIEW"), eq("TREND"), eq(before.id()), eq("SUCCESS"), any());
    }

    @Test
    void aiFailedContentNeedsAnExplicitHumanNoteBeforeOverride() {
        TrendModerationItem failed = moderationItem(TrendModerationStatus.AI_FAILED);
        when(repository.findModeration(failed.id())).thenReturn(java.util.Optional.of(failed));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.finalizeHuman("admin", failed.id(), "APPROVED", "  "));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(repository, never()).finalizeHuman(any(), any(), any(), any(), any());
    }

    private static TrendModerationItem moderationItem(String status) {
        return new TrendModerationItem(
                "weibo:test-moderation",
                "weibo",
                "通勤穿搭",
                List.of("通勤"),
                72,
                Instant.parse("2026-09-14T08:00:00Z"),
                Instant.parse("2026-09-15T08:00:00Z"),
                "https://example.com/trend",
                status.equals(TrendModerationStatus.REJECTED),
                null,
                "摘要",
                status,
                "PASS",
                "LOW",
                "相关",
                "qwen-plus",
                "call-1",
                "trend-moderation-v1",
                Instant.parse("2026-09-15T08:00:00Z"),
                status.equals(TrendModerationStatus.APPROVED) ? "admin" : null,
                status.equals(TrendModerationStatus.APPROVED) ? Instant.parse("2026-09-15T08:01:00Z") : null,
                null);
    }
}
