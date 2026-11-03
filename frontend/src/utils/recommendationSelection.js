export function recommendationSelectionError(selection, wardrobe) {
  const locked = selection.lockedItemIds || []
  const excluded = selection.excludedItemIds || []
  if (locked.length > 4) return '最多指定 4 件必须使用的衣物。'
  if (excluded.length > 100) return '最多排除 100 件衣物。'
  if (locked.some(id => excluded.includes(id))) return '同一件衣物不能同时指定使用和排除。'
  const available = new Set(wardrobe.filter(item => item.recognitionStatus !== 'NEEDS_MANUAL_REVIEW').map(item => item.id))
  if ([...locked, ...excluded].some(id => !available.has(id))) return '所选衣物已删除或需要确认，请重新选择本次使用的衣物。'
  return ''
}
