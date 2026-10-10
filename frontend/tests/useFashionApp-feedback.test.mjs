import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { useFashionApp } from '../src/composables/useFashionApp.js'

const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch })

function response(data) {
  return new Response(JSON.stringify({ code: 0, message: 'ok', data }), { headers: { 'content-type': 'application/json' } })
}

function fixture(onFeedback) {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.authUser = { username: 'feedback-user', authorities: [] }
  const recommendation = { id: 7, summary: '参考搭配', items: [{ style: '极简', color: '米白' }], feedback: { rating: 3, feedbackType: 'too_hot', comment: '喜欢配色，但偏热' } }
  app.state.history = [recommendation]
  app.state.currentRecommendation = recommendation
  app.state.profile = { stylePreferences: [], colorPreferences: [], occasions: [], preferencesConfirmed: false }
  const calls = []
  globalThis.fetch = async (url, options = {}) => {
    const path = new URL(url, 'http://localhost').pathname
    if (path.endsWith('/csrf')) return response({ token: 'feedback-csrf', headerName: 'X-XSRF-TOKEN' })
    if (path.endsWith('/logout')) return response(null)
    if (path.endsWith('/feedback')) {
      const body = JSON.parse(options.body)
      calls.push(body)
      return onFeedback ? onFeedback(body) : response({ ...recommendation, feedback: body })
    }
    throw new Error('Unexpected request: ' + path)
  }
  return { app, recommendation, calls }
}

test('changing stars preserves the saved reason and comment and updates history', async () => {
  const { app, recommendation, calls } = fixture()
  const updated = await app.rateRecommendation(recommendation, 4)
  assert.deepEqual(calls, [{ rating: 4, feedbackType: 'too_hot', comment: '喜欢配色，但偏热' }])
  assert.equal(app.state.history[0].feedback.rating, 4)
  assert.equal(updated.feedback.comment, '喜欢配色，但偏热')
  assert.deepEqual(app.state.profile.stylePreferences, [])
})

test('feedback can be explicitly edited and cleared without silently confirming preferences', async () => {
  const { app, recommendation, calls } = fixture()
  await app.rateRecommendation(recommendation, 5, { feedbackType: 'color_like', comment: '喜欢米白' })
  await app.rateRecommendation(app.state.currentRecommendation, 4, { feedbackType: 'rating', comment: '' })
  assert.equal(calls[0].feedbackType, 'color_like')
  assert.equal(calls[1].comment, '')
  assert.equal(app.state.profile.preferencesConfirmed, false)
})

test('a feedback response after logout is discarded', async () => {
  let complete
  const pending = new Promise(resolve => { complete = resolve })
  const { app, recommendation } = fixture(() => pending)
  const saving = app.rateRecommendation(recommendation, 5)
  await new Promise(setImmediate)
  await app.logout()
  complete(response({ ...recommendation, feedback: { rating: 5 } }))
  assert.equal(await saving, null)
  assert.deepEqual(app.state.history, [])
})

test('exploring a saved outfit creates a pending suggestion and clears it on logout', async (t) => {
  const originalWindow = globalThis.window
  t.after(() => {
    if (originalWindow === undefined) delete globalThis.window
    else globalThis.window = originalWindow
  })
  globalThis.window = {
    location: { hash: '' },
    history: { pushState(_state, _title, hash) { globalThis.window.location.hash = hash } },
    scrollTo() {}
  }
  const { app, recommendation } = fixture()
  app.exploreRecommendationPreferences(recommendation)
  assert.equal(app.state.activeView, 'profile')
  assert.equal(globalThis.window.location.hash, '#profile')
  assert.deepEqual(app.state.preferenceInspiration.stylePreferences, ['极简'])
  assert.deepEqual(app.state.preferenceInspiration.colorPreferences, ['米白'])
  assert.deepEqual(app.state.profile.stylePreferences, [])
  await app.logout()
  assert.equal(app.state.preferenceInspiration, null)
})
