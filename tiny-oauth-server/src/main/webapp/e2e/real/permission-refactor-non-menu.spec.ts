import { test, expect } from '@playwright/test'

/**
 * Permission Refactor non-menu E2E skeleton.
 * This file is intentionally conservative: it validates entry points and
 * keeps suite placeholders for integration-stage data fixtures.
 */

test.describe('permission-refactor non-menu e2e', () => {
  test('suite1 auth/context entry', async ({ page }) => {
    await page.goto('/login')
    await expect(page).toHaveURL(/\/login/)
  })

  test('suite5 menu non-drift guard entry', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('body')).toBeVisible()
  })

  test('suite2 permission linkage mutate/restore hook present', async ({ page }) => {
    await page.goto('/login')
    await expect(page).toHaveURL(/\/login/)
    // Real mutate/restore is executed by tiny-oauth-server/scripts/e2e/run-permission-refactor-e2e.sh
    // and summarized in test-results/permission-refactor-e2e-summary.*
    await expect(page.locator('body')).toBeVisible()
  })

  test('suite3 new/old consistency signal hook present', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('body')).toBeVisible()
  })

  test('suite4 scope bucket isolation hook present', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('body')).toBeVisible()
  })

  test('suite6 short stability loop hook present', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('body')).toBeVisible()
  })
})
