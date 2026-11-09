export function outfitModelReferenceKey(profile) {
  return profile?.usePersonalPhotoForOutfit ? `PERSONAL:${profile.photoUrl || 'missing'}` : 'DEFAULT'
}

export function outfitVisualItemKey(items) {
  return JSON.stringify((items || []).map(item => [item.id, item.name, item.category, item.color,
    item.style, item.uploaded ? item.id : item.imageUrl, item.recognitionStatus]))
}
