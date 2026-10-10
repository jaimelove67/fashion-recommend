export const STYLE_REFERENCES = [
  { id: 'minimal', title: '简洁耐看', description: '基础轮廓、低调配色，细节简洁。', styles: ['极简'], colors: ['米白', '灰色'], palette: ['#eee8dc', '#63716a', '#b9bdb8'], shape: 'minimal' },
  { id: 'casual', title: '轻松休闲', description: '宽松上衣与直筒裤，适合日常出门。', styles: ['休闲'], colors: ['浅蓝', '米白'], palette: ['#9cb5c8', '#dfd4ba', '#f1ece2'], shape: 'casual' },
  { id: 'smart', title: '利落得体', description: '衬衫、外搭与长裤，轮廓干净。', styles: ['商务休闲'], colors: ['藏青', '米白'], palette: ['#eee8dc', '#384d60', '#85715b'], shape: 'smart' },
  { id: 'expressive', title: '鲜明有个性', description: '色彩对比与叠穿层次，更有存在感。', styles: ['街头'], colors: ['绿色', '黑色'], palette: ['#71895b', '#343b36', '#c99074'], shape: 'expressive' }
]

export const PREFERENCE_FIELDS = [
  { key: 'stylePreferences', label: '喜欢的风格', hint: '可选多个，也可以暂不确定。', options: ['极简', '休闲', '商务休闲', '学院', '运动', '复古', '街头'] },
  { key: 'colorPreferences', label: '喜欢的颜色', hint: '只选自己喜欢的颜色；参考配色可以调整。', options: ['米白', '黑色', '灰色', '浅蓝', '藏青', '卡其', '绿色', '棕色', '粉色', '红色'] },
  { key: 'occasions', label: '常用场合', hint: '场合与风格分别保存。', options: ['上课', '通勤', '周末出门', '运动', '约会', '正式会议'] },
  { key: 'avoidPreferences', label: '想避开的元素', hint: '明确不喜欢的内容；不确定可以留空。', options: ['紧身', '大面积印花', '过于正式', '鲜艳配色'] }
]

export const FEELING_OPTIONS = [
  { label: '简洁耐看', styles: ['极简'] },
  { label: '轻松随意', styles: ['休闲'] },
  { label: '利落正式', styles: ['商务休闲'] },
  { label: '鲜明有个性', styles: ['街头'] }
]

export function preferenceList(value) {
  const values = Array.isArray(value) ? value : String(value || '').split(/[,，、\n]/)
  return [...new Set(values.filter(item => typeof item === 'string').map(item => item.trim()).filter(Boolean))]
}

export function suggestPreferences(choices, feelings = [], explicitColors = []) {
  const references = STYLE_REFERENCES.filter(item => ['like', 'try'].includes(choices[item.id]))
  return {
    styles: preferenceList([...references.flatMap(item => item.styles), ...FEELING_OPTIONS.filter(item => feelings.includes(item.label)).flatMap(item => item.styles)]),
    colors: preferenceList(explicitColors.length ? explicitColors : references.flatMap(item => item.colors)),
    references: references.map(item => item.title)
  }
}

export const FEEDBACK_REASONS = [
  { value: 'color_like', label: '喜欢这套配色' },
  { value: 'fit_like', label: '喜欢这种轮廓' },
  { value: 'style_explore', label: '想再试这个方向' },
  { value: 'occasion_mismatch', label: '不适合本次场合' },
  { value: 'too_hot', label: '穿起来太热' },
  { value: 'too_cold', label: '穿起来太冷' }
]
