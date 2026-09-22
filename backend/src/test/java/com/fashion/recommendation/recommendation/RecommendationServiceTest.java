package com.fashion.recommendation.recommendation;

import com.fashion.recommendation.style.PersonalStyleProfileService;
import com.fashion.recommendation.wardrobe.WardrobeRepository;
import com.fashion.recommendation.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {
    @Test
    void stopsPaginationAtOffsetLimitEvenWhenMoreRecordsExist() {
        RecommendationRepository repository = mock(RecommendationRepository.class);
        RecommendationService service = new RecommendationService(
                mock(WardrobeRepository.class),
                repository,
                mock(RecommendationFeedbackRepository.class),
                mock(PersonalStyleProfileService.class),
                mock(WeatherService.class),
                mock(LlmRecommendationClient.class),
                mock(TransactionTemplate.class), mock(com.fashion.recommendation.trend.TrendService.class));
        when(repository.countByUserId("large-history-user")).thenReturn(1_000_051L);

        assertTrue(service.list("large-history-user", 19_999, 50).hasNext());
        assertFalse(service.list("large-history-user", 20_000, 50).hasNext());
    }
}
