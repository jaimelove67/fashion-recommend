package com.fashion.recommendation.assistant;

import com.fashion.recommendation.wardrobe.WardrobeRepository;
import com.fashion.recommendation.wardrobe.WardrobeItem;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ConsultationService {
    private final WardrobeRepository wardrobeRepository;
    private final ConsultationClient client;

    public ConsultationService(WardrobeRepository wardrobeRepository, ConsultationClient client) {
        this.wardrobeRepository = wardrobeRepository;
        this.client = client;
    }

    public ConsultationResponse consult(String userId, ConsultationRequest request) {
        // An empty or incomplete wardrobe never blocks consultation. Bound prompt size.
        String recent = request.history() == null ? "" : request.history().stream()
                .filter(turn -> "user".equals(turn.role())).map(ConsultationRequest.Turn::text).collect(Collectors.joining(" "));
        var wardrobe = wardrobeRepository.findByUserId(userId).stream()
                .filter(item -> !"NEEDS_MANUAL_REVIEW".equals(item.recognitionStatus()))
                .sorted(Comparator.comparingInt((WardrobeItem item) ->
                        overlap(item, request.question()) * 4 + overlap(item, recent)).reversed())
                .limit(80).toList();
        var answer = client.answer(request, wardrobe);
        var examples = answer.exampleItemIds().stream().map(id -> wardrobe.stream()
                .filter(item -> item.id().equals(id)).findFirst().orElseThrow(ConsultationClient::invalid)).toList();
        return new ConsultationResponse(answer.answer(), examples, answer.visuals());
    }

    private static int overlap(WardrobeItem item, String text) {
        String description = String.join(" ", item.name(), item.category(), item.color());
        int score = 0;
        for (int i = 0; i < description.length() - 1; i++) {
            String term = description.substring(i, i + 2);
            if (!term.isBlank() && text.contains(term)) score++;
        }
        // Color names often differ in depth (e.g. 蓝色 / 深蓝); preserve that broad match.
        for (String color : new String[] {"蓝", "白", "黑", "灰", "红", "绿", "黄", "紫", "棕", "粉", "卡其"}) {
            if (item.color().contains(color) && text.contains(color)) score += 2;
        }
        return score;
    }
}
