package com.fashion.recommendation.recommendation;

import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationFeedbackRepositoryTest {
    @Test
    void contextComplaintsDoNotBecomeClothingDislikesAndOtherUsersAreExcluded() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:feedback_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE recommendation_items (recommendation_id BIGINT, wardrobe_item_id BIGINT)");
        jdbc.execute("CREATE TABLE recommendation_feedback (recommendation_id BIGINT, user_id VARCHAR(100), rating INT, feedback_type VARCHAR(80))");
        String[] reasons = {"rating", "color_like", "too_hot", "too_cold", "occasion_mismatch", null};
        int[] ratings = {4, 5, 1, 2, 1, 3};
        for (int i = 0; i < reasons.length; i++) {
            jdbc.update("INSERT INTO recommendation_items VALUES (?, 10)", i + 1);
            jdbc.update("INSERT INTO recommendation_feedback VALUES (?, 'user-a', ?, ?)", i + 1, ratings[i], reasons[i]);
        }
        jdbc.update("INSERT INTO recommendation_items VALUES (7, 10), (8, 11)");
        jdbc.update("INSERT INTO recommendation_feedback VALUES (7, 'user-b', 1, 'rating'), (8, 'user-a', 1, 'too_hot')");
        assertEquals(Map.of(10L, 4.0), new RecommendationFeedbackRepository(jdbc).averageRatingByItem("user-a"));
    }
}
