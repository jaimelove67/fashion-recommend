import assert from 'node:assert/strict'
import test from 'node:test'
import { formatRecommendationReason, formatRecommendationTitle } from '../src/utils/recommendationReason.js'

const items = [
  { name: '米白翻领衬衫', color: '米白', style: '通勤' },
  { name: '深蓝牛仔裤', color: '深蓝', style: '通勤' },
  { name: '黑色系带皮鞋', color: '黑色', style: '通勤' }
]

test('legacy titles use confirmed palette and occasion without exposing profile details or rewriting records', () => {
  const record = { summary: '推荐米白衬衫与深蓝长裤，整体适合长沙当前21.8°C天气及方脸女性172cm身高所需的穿搭比例。', occasion: '通勤', items }
  const original = JSON.stringify(record)
  assert.equal(formatRecommendationTitle(record), '米白与深蓝 · 通勤搭配')
  assert.equal(JSON.stringify(record), original)
  assert.equal(formatRecommendationTitle({ summary: '米白与深蓝的简约通勤搭配', items }), '米白与深蓝的简约通勤搭配')
  assert.equal(formatRecommendationTitle({ occasion: '场合'.repeat(100), items }), '衣橱搭配方案')
  assert.equal(formatRecommendationTitle(null), '衣橱搭配')
})

test('turns a legacy model explanation into supported complete sentences without changing the source', () => {
  const reason = '用户为172cm女性，人工确认方脸，米白翻领衬衫（ID:40）精准响应；其挺括质地适合通勤。深蓝牛仔裤（ID:41）为直筒版型，优化身高比例；深蓝与米白的配色沉稳，黑色皮鞋增加正式感。三件无反馈评分缺失，组合逻辑闭环，完全覆盖需求。'
  const record = { reason, items }
  assert.equal(formatRecommendationReason(record), '深蓝与米白的配色沉稳，黑色皮鞋增加正式感。')
  assert.equal(record.reason, reason)
})

test('preserves concise reasons based on confirmed garment details', () => {
  const reason = '米白翻领衬衫呼应你确认的领口偏好。黑色皮鞋增加通勤的正式感。'
  assert.equal(formatRecommendationReason({ reason, items }), reason)
})

test('does not transfer one garment attribute to another garment', () => {
  const record = { reason: '米白衬衫采用直筒版型。米白与深蓝搭配简洁。', items: [
    { name: '米白衬衫', style: '通勤' }, { name: '深蓝直筒裤', style: '通勤' }
  ] }
  assert.equal(formatRecommendationReason(record), '米白与深蓝搭配简洁。')
})

test('bounds lengthy explanations using complete sentences and handles missing reasons', () => {
  const first = '米白和深蓝的配色适合通勤'
  const record = { reason: first + '。' + '很长的说明'.repeat(50) + '。皮鞋补充正式感。', items }
  assert.equal(formatRecommendationReason(record), first + '。皮鞋补充正式感。')
  assert.equal(formatRecommendationReason({ reason: '直筒版型适合你。', items }), '所选单品的通勤风格保持一致，组合更协调。')
  assert.equal(formatRecommendationReason(null), '')
})

test('keeps the meaning of a stale-analysis notice when removing legacy process details', () => {
  const record = { reason: '米白与深蓝搭配简洁。形象资料已变更，旧分析暂未用于本次推荐，请重新分析或人工确认。', items }
  assert.equal(formatRecommendationReason(record), '形象资料已变更，这次推荐参考当前基础资料和偏好。米白与深蓝搭配简洁。')
})

test('retains useful palette clauses mixed with unconfirmed fit and measurement claims', () => {
  const record = { reason: '长沙当前气温21.8℃、无降水、微风，属舒适通勤环境。深蓝牛仔裤（ID:41）为直筒版型，优化比例，与172cm身高匹配，深蓝色低饱和、沉稳，与米白形成中性对比。黑色系带皮鞋增加通勤的正式感，呼应身高优势。', items }
  assert.equal(formatRecommendationReason(record), '深蓝色低饱和、沉稳，与米白形成中性对比。黑色系带皮鞋增加通勤的正式感。')
})
