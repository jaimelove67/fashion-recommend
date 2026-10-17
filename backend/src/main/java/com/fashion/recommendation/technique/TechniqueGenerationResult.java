package com.fashion.recommendation.technique;

/** Only the bounded synthesis is published; the reviewer score and call IDs stay in the database. */
public record TechniqueGenerationResult(OutfitTechnique technique, int qualityScore, String providerCallId) {}
