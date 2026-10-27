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

function adminFixture() {
  const app = useFashionApp()
  app.state.authPhase = 'authenticated'
  app.state.authUser = { username: 'review-admin', authorities: ['ROLE_ADMIN'] }
  const requests = []
  globalThis.fetch = async input => {
    const url = new URL(input, 'http://localhost')
    if (url.pathname.endsWith('/csrf')) return response({ token: 'fixture', headerName: 'X-XSRF-TOKEN' })
    if (url.pathname.endsWith('/logout')) return response(null)
    return new Promise(resolve => requests.push({ status: url.searchParams.get('status'), resolve }))
  }
  return { app, requests }
}

function feedbackPage(status) {
  return response({ items: [{ recommendationId: 7, moderationStatus: status }], totalElements: 1, page: 0, hasNext: false })
}

for (const responseOrder of ['old-first', 'new-first']) {
  test(`admin feedback uses the newest filter when responses finish ${responseOrder}`, async () => {
    const { app, requests } = adminFixture()
    const oldLoad = app.loadAdminFeedback()
    app.state.adminFeedbackStatus = 'RESOLVED'
    const newLoad = app.loadAdminFeedback()
    assert.deepEqual(requests.map(request => request.status), ['PENDING', 'RESOLVED'])

    if (responseOrder === 'old-first') {
      requests[0].resolve(feedbackPage('PENDING'))
      await oldLoad
      assert.deepEqual(app.state.adminFeedback, [])
      assert.equal(app.state.adminFeedbackLoading, true)
      requests[1].resolve(feedbackPage('RESOLVED'))
      await newLoad
    } else {
      requests[1].resolve(feedbackPage('RESOLVED'))
      await newLoad
      assert.equal(app.state.adminFeedbackLoading, false)
      requests[0].resolve(feedbackPage('PENDING'))
      await oldLoad
    }
    assert.equal(app.state.adminFeedback[0].moderationStatus, 'RESOLVED')
    assert.equal(app.state.adminFeedbackLoading, false)
  })
}

test('an obsolete feedback error does not replace the current result or clear its loading flag', async () => {
  const { app, requests } = adminFixture()
  const oldLoad = app.loadAdminFeedback()
  app.state.adminFeedbackStatus = 'RESOLVED'
  const newLoad = app.loadAdminFeedback()
  requests[0].resolve(new Response(JSON.stringify({ code: 500, message: 'Obsolete error' }), { status: 500 }))
  await oldLoad
  assert.equal(app.state.error, '')
  assert.equal(app.state.adminFeedbackLoading, true)
  assert.equal(requests.length, 2)
  requests[1].resolve(feedbackPage('RESOLVED'))
  await newLoad
  assert.equal(app.state.adminFeedback[0].moderationStatus, 'RESOLVED')
})

test('the newest feedback error stays visible when an older successful response arrives', async () => {
  const { app, requests } = adminFixture()
  const oldLoad = app.loadAdminFeedback()
  app.state.adminFeedbackStatus = 'RESOLVED'
  const newLoad = app.loadAdminFeedback()
  assert.equal(requests.length, 2)
  requests[1].resolve(new Response(JSON.stringify({ code: 500, message: 'Current error' }), { status: 500 }))
  await newLoad
  requests[0].resolve(feedbackPage('PENDING'))
  await oldLoad
  assert.equal(app.state.error, 'Current error')
  assert.deepEqual(app.state.adminFeedback, [])
  assert.equal(app.state.adminFeedbackLoading, false)
})

test('feedback from the previous session cannot replace the new account data or loading state', async () => {
  const { app, requests } = adminFixture()
  const oldLoad = app.loadAdminFeedback()
  await app.logout()
  app.state.authPhase = 'authenticated'
  app.state.authUser = { username: 'second-admin', authorities: ['ROLE_ADMIN'] }
  app.state.adminFeedbackStatus = 'RESOLVED'
  const newLoad = app.loadAdminFeedback()
  requests[0].resolve(feedbackPage('PENDING'))
  await oldLoad
  assert.deepEqual(app.state.adminFeedback, [])
  assert.equal(app.state.adminFeedbackLoading, true)
  requests[1].resolve(feedbackPage('RESOLVED'))
  await newLoad
  assert.equal(app.state.adminFeedback[0].moderationStatus, 'RESOLVED')
})

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
