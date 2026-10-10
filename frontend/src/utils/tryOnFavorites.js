import { imageFile } from './tryOnImages.js'

export function favoriteUploadForm(item, imageData, username) {
  const form = new FormData()
  const blob = imageFile(imageData)
  form.append('image', blob, 'garment.' + blob.type.split('/')[1])
  form.append('name', item.name || '试穿单品')
  form.append('category', item.category)
  form.append('sourceUrl', item.sourceUrl || '')
  form.append('sourceKind', item.sourceKind || 'TREND_ILLUSTRATION')
  form.append('expectedUser', username)
  return form
}

export function favoriteTryOnItem(item) {
  return { ...item, imageSource: 'zhiji_saved_favorite', imageNote: item.sourceKind === 'TREND_ILLUSTRATION'
    ? '趋势单品示意图 · 非原帖商品图' : '已收藏的商品图片' }
}
