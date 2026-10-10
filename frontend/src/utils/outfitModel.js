export function outfitModelReferenceKey(profile) {
  return profile?.usePersonalPhotoForOutfit ? `PERSONAL:${profile.photoUrl || 'missing'}` : 'DEFAULT'
}
