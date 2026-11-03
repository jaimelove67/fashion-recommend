// Ambiguous requests stay in consultation; choosing the wardrobe mode is also explicit consent.
export function isWardrobeGenerationRequest(text) {
  const value = String(text || '').replace(/\s/g, '')
  if (/(不要|不用|不想|别|无需).{0,20}(生成|搭配|推荐|选)/.test(value)) return false
  if (/怎么|如何|怎样|为什么|教程|技巧|原理|是什么|区别|注意什么|应该|需要几|多少|几种|几件|是否|能否|合适吗|适合吗/.test(value)) return false
  const wardrobe = /衣橱|我(?:的|上传的|已有的|现有的)衣服|我上传的单品/.test(value)
  // Asking for colors, garment advice or selection tips is not asking for a complete outfit.
  const completeOutfit = /生成.{0,20}(?:搭配|穿搭|一套|一身)/.test(value)
    || /(?:搭配|推荐|选|挑).{0,12}(?:一套|一身|整套|完整搭配|搭配方案)/.test(value)
    || /(?:一套|一身|整套).{0,12}(?:搭配|穿搭)/.test(value)
  return wardrobe && completeOutfit
}
