package com.fashion.recommendation.technique;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Only bundled, human-written lessons are accepted; there is no public prompt/import endpoint. */
@Component
public class CuratedTechniqueCatalog {
    private final List<OutfitTechnique> items;

    public CuratedTechniqueCatalog(ObjectMapper mapper) {
        try (var input = new ClassPathResource("techniques/curated.json").getInputStream()) {
            items = List.of(mapper.readValue(input, OutfitTechnique[].class));
        } catch (Exception exception) {
            throw new IllegalStateException("Bundled technique catalog could not be read", exception);
        }
    }

    public List<OutfitTechnique> items() { return items; }
}
