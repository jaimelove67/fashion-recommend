package com.fashion.recommendation.style;

import jakarta.validation.constraints.NotNull;

public record OutfitModelPreferenceRequest(@NotNull Boolean usePersonalPhotoForOutfit) {
}
