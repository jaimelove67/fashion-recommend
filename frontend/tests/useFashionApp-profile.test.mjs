import assert from 'node:assert/strict'
import { test, afterEach } from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

function response(data, status = 200, message = 'ok') {
  return new Response(JSON.stringify({ code: status < 400 ? 0 : status, message, data }), {
    status, headers: { 'content-type': 'application/json' }
  })
}

function profile() {
  return {
    displayName: '用户', gender: 'MALE', heightCm: 175, weightKg: 65,
    stylePreferences: ['通勤'], colorPreferences: ['米白'], occasions: ['通勤'],
    photoUrl: '/api/v1/me/style-profile/photo?v=1', analysis: null, stale: false, usePersonalPhotoForOutfit: false
  }
}

function fixture({ onAnalyze, onVisual, onRefresh } = {}) {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.authUser = { username: 'profile-user', authorities: [] }
  app.state.profile = profile()
  app.state.profileForm = { ...profile(), stylePreferences: '通勤', colorPreferences: '米白', occasions: '通勤' }
  let stored = profile()
  const calls = []
  globalThis.fetch = async (input, options = {}) => {
    const path = new URL(String(input), 'http://localhost').pathname
    const call = { path, options }
    calls.push(call)
    if (path.endsWith('/csrf')) return response({ token: 'test-csrf', headerName: 'X-XSRF-TOKEN' })
    if (path.endsWith('/logout')) return response(null)
    if (path.endsWith('/refresh')) {
      if (onRefresh) return onRefresh(call)
      stored = { ...stored, ...JSON.parse(options.body) }
      return response(stored)
    }
    if (path.endsWith('/photo')) {
      assert.ok(options.body instanceof FormData)
      assert.equal(options.body.get('photo').type, 'image/png')
      stored = { ...stored, photoUrl: '/api/v1/me/style-profile/photo?v=2', usePersonalPhotoForOutfit: false }
      return response(stored)
    }
    if (path.endsWith('/outfit-model')) {
      stored = { ...stored, ...JSON.parse(options.body) }
      return response(stored)
    }
    if (path.endsWith('/visual')) {
      return onVisual ? onVisual(call) : response({ status: 'SUCCEEDED', imageUrl: 'https://images.test/look.png' })
    }
    if (path.endsWith('/analyze')) {
      call.body = JSON.parse(options.body)
      if (onAnalyze) return onAnalyze(call)
      stored = { ...stored, analysis: { faceShape: '椭圆偏长', fitSuggestions: ['圆领'] }, analysisSource: 'MODEL' }
      return response(stored)
    }
    if (path.endsWith('/analysis')) {
      stored = { ...stored, analysis: JSON.parse(options.body), analysisSource: 'MANUAL' }
      return response(stored)
    }
    throw new Error('Unexpected request: ' + path)
  }
  return { app, calls }
}

test('profile measurements, photo, analysis, and manual correction use authenticated backend writes', async () => {
  const { app, calls } = fixture()
  const saved = await app.saveProfile({ heightCm: 180, weightKg: 70 })
  assert.equal(saved.heightCm, 180)
  assert.equal(app.state.profileForm.weightKg, 70)
  const file = new File([new Uint8Array([1, 2, 3])], 'portrait.png', { type: 'image/png' })
  assert.ok(await app.uploadProfilePhoto(file))
  assert.ok(await app.analyzeProfile(true))
  assert.equal(app.state.profile.analysis.faceShape, '椭圆偏长')
  assert.ok(await app.saveProfileAnalysis({ faceShape: '方脸', fitSuggestions: ['V领'] }))
  assert.equal(app.state.profile.analysis.faceShape, '方脸')
  assert.equal(app.state.profile.analysisSource, 'MANUAL')
  for (const { options } of calls.filter((call) => call.options.method === 'POST')) {
    assert.equal(options.credentials, 'same-origin')
    assert.equal(new Headers(options.headers).get('X-XSRF-TOKEN'), 'test-csrf')
  }
  assert.equal(app.state.profileSaving, false)
  assert.equal(app.state.profileAnalyzing, false)
  assert.equal(app.state.profilePhotoUploading, false)
})

test('profile analysis requires explicit consent before dispatching a model request', async () => {
  const { app, calls } = fixture()
  assert.equal(await app.analyzeProfile(), null)
  assert.equal(calls.length, 0)
  assert.match(app.state.error, /授权/)
})

test('confirmed partial preferences use saved measurements without persisting unrelated form drafts', async () => {
  const { app, calls } = fixture()
  app.state.profileForm.gender = 'FEMALE'
  app.state.profileForm.heightCm = 199
  const saved = await app.savePreferences({ stylePreferences: [], colorPreferences: ['浅蓝'], occasions: [], avoidPreferences: ['紧身'] })
  const body = JSON.parse(calls.find(call => call.path.endsWith('/refresh')).options.body)
  assert.deepEqual(body.stylePreferences, [])
  assert.deepEqual(body.colorPreferences, ['浅蓝'])
  assert.deepEqual(body.avoidPreferences, ['紧身'])
  assert.equal(body.confirmPreferences, true)
  assert.equal(body.gender, 'MALE')
  assert.equal(body.heightCm, 175)
  assert.equal(body.weightKg, 65)
  assert.equal(saved.photoUrl, '/api/v1/me/style-profile/photo?v=1')
})

test('a preference save finishing after logout cannot restore the previous profile', async () => {
  let complete
  const pending = new Promise(resolve => { complete = resolve })
  const { app } = fixture({ onRefresh: () => pending })
  const saving = app.savePreferences({ stylePreferences: ['极简'] })
  await new Promise(setImmediate)
  await app.logout()
  complete(response({ ...profile(), stylePreferences: ['极简'], preferencesConfirmed: true }))
  assert.equal(await saving, null)
  assert.equal(app.state.profile, null)
  assert.equal(app.state.profileSaving, false)
})

test('failed model analysis preserves saved measurements and photo without inventing a result', async () => {
  const { app } = fixture({ onAnalyze: () => response(null, 503, '视觉识别未启用') })
  assert.equal(await app.analyzeProfile(true), null)
  assert.equal(app.state.profile.heightCm, 175)
  assert.equal(app.state.profile.photoUrl, '/api/v1/me/style-profile/photo?v=1')
  assert.equal(app.state.profile.analysis, null)
  assert.equal(app.state.profileAnalyzing, false)
  assert.equal(app.state.error, '视觉识别未启用')
})

test('an analysis response arriving after logout cannot restore private profile data', async () => {
  let complete
  const pending = new Promise((resolve) => { complete = resolve })
  const { app, calls } = fixture({ onAnalyze: () => pending })
  const request = app.analyzeProfile(true)
  await new Promise(setImmediate)
  assert.equal(calls.filter((call) => call.path.endsWith('/analyze')).length, 1)
  assert.equal(await app.logout(), true)
  complete(response({ ...profile(), analysis: { faceShape: '旧账号的分析' } }))
  assert.equal(await request, null)
  assert.equal(app.state.profile, null)
  assert.equal(app.state.profileAnalyzing, false)
})

test('personal-photo outfit choice is a saved preference separate from analysis consent', async () => {
  const { app, calls } = fixture()
  const saved = await app.saveOutfitModelPreference(true)
  assert.equal(saved.usePersonalPhotoForOutfit, true)
  const call = calls.find(call => call.path.endsWith('/outfit-model'))
  assert.deepEqual(JSON.parse(call.options.body), { usePersonalPhotoForOutfit: true })
  assert.equal(await app.analyzeProfile(), null)
  assert.equal(calls.filter(call => call.path.endsWith('/analyze')).length, 0)
  await app.saveOutfitModelPreference(false)
  assert.equal(app.state.profile.usePersonalPhotoForOutfit, false)
})

test('outfit image cache changes when the selected person or uploaded photo changes', async () => {
  const { app, calls } = fixture()
  const look = { id: 5, items: [{ id: 1 }, { id: 2 }] }
  await app.generateRecommendationVisual(look)
  await app.generateRecommendationVisual(look)
  assert.equal(calls.filter(call => call.path.endsWith('/visual')).length, 1)
  await app.saveOutfitModelPreference(true)
  await app.generateRecommendationVisual(look)
  assert.equal(calls.filter(call => call.path.endsWith('/visual')).length, 2)
  app.state.profile.photoUrl = '/api/v1/me/style-profile/photo?v=3'
  await app.generateRecommendationVisual(look)
  assert.equal(calls.filter(call => call.path.endsWith('/visual')).length, 3)
  await app.saveOutfitModelPreference(false)
  await app.generateRecommendationVisual(look)
  assert.equal(calls.filter(call => call.path.endsWith('/visual')).length, 3)
})

test('replacing an uploaded photo requires opting in again before using it for outfits', async () => {
  const { app } = fixture()
  await app.saveOutfitModelPreference(true)
  await app.uploadProfilePhoto(new File([new Uint8Array([1, 2, 3])], 'replacement.png', { type: 'image/png' }))
  assert.equal(app.state.profile.usePersonalPhotoForOutfit, false)
})

test('a personal outfit image finishing after logout is discarded', async () => {
  let complete
  const pending = new Promise(resolve => { complete = resolve })
  const { app } = fixture({ onVisual: () => pending })
  await app.saveOutfitModelPreference(true)
  const generating = app.generateRecommendationVisual({ id: 5, items: [{ id: 1 }, { id: 2 }] })
  await new Promise(setImmediate)
  await app.logout()
  complete(response({ status: 'SUCCEEDED', imageUrl: 'https://images.test/private-look.png' }))
  assert.equal(await generating, null)
  assert.equal(app.state.profile, null)
})

test('a personal outfit image finishing after opting out is discarded', async () => {
  let complete
  const pending = new Promise(resolve => { complete = resolve })
  const { app } = fixture({ onVisual: () => pending })
  await app.saveOutfitModelPreference(true)
  const generating = app.generateRecommendationVisual({ id: 5, items: [{ id: 1 }, { id: 2 }] })
  await new Promise(setImmediate)
  await app.saveOutfitModelPreference(false)
  complete(response({ status: 'SUCCEEDED', imageUrl: 'https://images.test/private-look.png' }))
  assert.equal(await generating, null)
  assert.equal(app.state.profile.usePersonalPhotoForOutfit, false)
})

test('switching person sources away and back discards an in-flight image without caching it', async () => {
  let complete
  const pending = new Promise(resolve => { complete = resolve })
  let visualCalls = 0
  const { app, calls } = fixture({ onVisual: () => ++visualCalls === 1 ? pending : response({
    status: 'SUCCEEDED', imageUrl: 'https://images.test/current.png', modelSource: 'DEFAULT', modelGender: 'MALE'
  }) })
  const look = { id: 5, items: [{ id: 1 }, { id: 2 }] }
  const generating = app.generateRecommendationVisual(look)
  await new Promise(setImmediate)
  await app.saveOutfitModelPreference(true)
  await app.saveOutfitModelPreference(false)
  complete(response({ status: 'SUCCEEDED', imageUrl: 'https://images.test/old.png', modelSource: 'DEFAULT', modelGender: 'MALE' }))
  assert.equal(await generating, null)
  assert.equal((await app.generateRecommendationVisual(look)).imageUrl, 'https://images.test/current.png')
  assert.equal(calls.filter(call => call.path.endsWith('/visual')).length, 2)
})

test('an image for a different person source or gender is never accepted or cached', async () => {
  for (const metadata of [{ modelSource: 'PERSONAL', modelGender: 'MALE' }, { modelSource: 'DEFAULT', modelGender: 'FEMALE' }]) {
    const { app, calls } = fixture({ onVisual: () => response({ status: 'SUCCEEDED', imageUrl: 'https://images.test/wrong.png', ...metadata }) })
    const look = { id: 5, items: [{ id: 1 }, { id: 2 }] }
    assert.equal(await app.generateRecommendationVisual(look), null)
    assert.equal(await app.generateRecommendationVisual(look), null)
    assert.equal(calls.filter(call => call.path.endsWith('/visual')).length, 2)
  }
})

test('changing model gender away and back invalidates an in-flight image', async () => {
  let complete
  const pending = new Promise(resolve => { complete = resolve })
  const { app } = fixture({ onVisual: () => pending })
  const generating = app.generateRecommendationVisual({ id: 5, items: [{ id: 1 }, { id: 2 }] })
  await new Promise(setImmediate)
  app.state.profile.gender = 'FEMALE'
  app.state.profile.gender = 'MALE'
  complete(response({ status: 'SUCCEEDED', imageUrl: 'https://images.test/old.png', modelSource: 'DEFAULT', modelGender: 'MALE' }))
  assert.equal(await generating, null)
})
