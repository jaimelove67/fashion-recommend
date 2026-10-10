import assert from 'node:assert/strict'
import test from 'node:test'
import {
  externalTrendSourceUrl,
  shouldShowStaleTrend,
  trendPlatformName,
  trendSourceCounts,
  trendSourceExclusionReason,
  trendSourceMessage
} from '../src/utils/trendSources.js'

test('formats collected and eligible source counts including zero', () => {
  assert.equal(trendSourceCounts({ itemCount: 3, eligibleCount: 0 }), '采集 3 条 / 可展示 0 条')
  assert.equal(trendSourceCounts({ itemCount: 2 }), '采集 2 条')
  assert.equal(trendSourceCounts({ eligibleCount: 0 }), '可展示 0 条')
})

test('keeps stale source state and exclusion reason visible', () => {
  assert.equal(trendSourceMessage({ state: 'stale', message: '' }), '已过期')
  assert.equal(trendSourceMessage({ state: 'stale', message: '最近 7 天无新内容' }), '已过期 · 最近 7 天无新内容')
  assert.equal(trendSourceExclusionReason({ exclusionReason: 'outside-window' }), '超出时间范围')
  assert.equal(trendSourceExclusionReason({ exclusionReason: '手动过滤' }), '手动过滤')
})

test('labels configured demo sources without breaking unknown legacy ids', () => {
  assert.equal(trendPlatformName('configured-demo'), '穿搭参考')
  assert.equal(trendPlatformName('legacy-source'), 'legacy-source')
})

test('does not expose local configured demo source URLs as external actions', () => {
  assert.equal(externalTrendSourceUrl({ platform: 'configured-demo', sourceUrl: '/' }), '')
  assert.equal(externalTrendSourceUrl({ platform: 'configured-demo', sourceUrl: 'https://example.test/demo' }), '')
  assert.equal(externalTrendSourceUrl({ platform: 'weibo', sourceUrl: 'https://weibo.com/post/1' }), 'https://weibo.com/post/1')
  assert.equal(externalTrendSourceUrl({ platform: 'weibo', sourceUrl: '/post/1' }), '')
})

test('does not show stale copy for configured demo trends', () => {
  assert.equal(shouldShowStaleTrend({ platform: 'configured-demo', stale: true }), false)
  assert.equal(shouldShowStaleTrend({ platform: 'weibo', stale: true }), true)
  assert.equal(shouldShowStaleTrend({ platform: 'weibo', stale: false }), false)
})
