package com.fashion.recommendation.assistant;

import com.fashion.recommendation.wardrobe.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsultationServiceTest {
    private final WardrobeRepository repository = mock(WardrobeRepository.class);
    private final ConsultationClient client = mock(ConsultationClient.class);
    private final ConsultationService service = new ConsultationService(repository, client);
    private final ConsultationRequest request = new ConsultationRequest("蓝色衬衫怎么配色？", List.of());

    @Test
    void emptyOrUnconfirmedWardrobeStillAnswersWithoutGeneration() {
        for (var items : List.of(List.<WardrobeItem>of(), List.of(new WardrobeItem(2L, "待完善", "待识别", "待识别",
                null, null, Instant.now(), "NEEDS_MANUAL_REVIEW", null, null)))) {
            when(repository.findByUserId("alice")).thenReturn(items);
            when(client.answer(request, List.of())).thenReturn(new ConsultationResponse.ModelAnswer("蓝配米白。", List.of(), List.of()));
            assertEquals("蓝配米白。", service.consult("alice", request).answer());
            assertTrue(service.consult("alice", request).examples().isEmpty());
        }
    }

    @Test
    void singleOwnedItemCanBeUsedAsAnExampleAndUnknownIdsAreRejected() {
        var shirt = new WardrobeItem(1L, "蓝衬衫", "上装", "蓝色", "通勤", "/shirt.png", Instant.now());
        when(repository.findByUserId("alice")).thenReturn(List.of(shirt));
        when(client.answer(request, List.of(shirt))).thenReturn(new ConsultationResponse.ModelAnswer("以蓝衬衫举例。", List.of(1L), List.of()));
        assertEquals(List.of(shirt), service.consult("alice", request).examples());
        when(client.answer(request, List.of(shirt))).thenReturn(new ConsultationResponse.ModelAnswer("非法示例", List.of(999L), List.of()));
        assertThrows(ResponseStatusException.class, () -> service.consult("alice", request));
        verify(repository, never()).findByUserId("bob");
    }

    @Test
    void relevantOlderItemIsNotLostWhenWardrobeExceedsPromptLimit() {
        var items = new java.util.ArrayList<WardrobeItem>();
        for (long id = 1; id <= 85; id++) items.add(new WardrobeItem(id, "黑长裤", "下装", "黑色", "休闲", null, Instant.now()));
        var shirt = new WardrobeItem(99L, "蓝色衬衫", "上装", "深蓝", "通勤", null, Instant.now());
        items.add(shirt);
        when(repository.findByUserId("alice")).thenReturn(items);
        when(client.answer(eq(request), anyList())).thenAnswer(call -> {
            List<WardrobeItem> context = call.getArgument(1);
            assertEquals(80, context.size());
            assertEquals(shirt, context.get(0));
            return new ConsultationResponse.ModelAnswer("以蓝衬衫举例。", List.of(99L), List.of());
        });
        assertEquals(List.of(shirt), service.consult("alice", request).examples());
    }
}
