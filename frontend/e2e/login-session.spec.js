import { expect, test } from '@playwright/test'

test('guest login reaches the real session service and same-origin requests pass CSRF validation', async ({ page }) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const csrfRequest = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/auth/csrf')
  await page.goto('/#home')
  const csrfResponse = await csrfRequest
  expect(csrfResponse.status()).toBe(200)
  const csrfBody = await csrfResponse.json()
  expect(csrfBody.code).toBe(0)
  expect(Boolean(csrfBody.data?.token && csrfBody.data?.headerName)).toBe(true)

  await page.getByRole('button', { name: '登录 / 注册', exact: true }).click()
  await expect(page.getByRole('heading', { name: '登录知己', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '登录知己', exact: true })).toBeEnabled()
  await expect(page.getByRole('alert')).toHaveCount(0)

  // Invalid input is rejected before account creation, while exercising the real proxy and CSRF flow.
  const validation = await page.evaluate(async () => {
    const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
    const { data } = await response.json()
    const registration = await fetch('/api/v1/auth/register', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', [data.headerName]: data.token },
      body: JSON.stringify({ username: '!', password: 'short' })
    })
    return { status: registration.status, code: (await registration.json()).code }
  })
  expect(validation).toEqual({ status: 400, code: 400 })
  expect(errors).toEqual([])
  await page.screenshot({ path: '../output/guest-home/login-session-fixed.png', animations: 'disabled' })
})
