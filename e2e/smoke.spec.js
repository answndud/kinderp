import { test, expect } from '@playwright/test';

async function loginAs(page, email) {
  await page.goto('/login');
  await page.getByLabel('이메일 주소').fill(email);
  await page.getByRole('textbox', { name: '비밀번호' }).fill('test1234!');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page).toHaveURL(/\/$|\/dashboard/);
}

async function loginAsPrincipal(page) {
  await loginAs(page, 'principal@test.com');
}

test('principal can log in and reach the operations dashboard', async ({ page }) => {
  await loginAsPrincipal(page);
  await expect(page.getByRole('link', { name: '대시보드', exact: true }).first()).toBeVisible();
});

test('principal home uses cached dashboard attendance statistics', async ({ page }) => {
  const dailyAttendanceRequests = [];
  const dashboardStatisticsRequests = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/v1/attendance/daily')) dailyAttendanceRequests.push(request.url());
    if (request.url().includes('/api/v1/dashboard/statistics')) dashboardStatisticsRequests.push(request.url());
  });

  await loginAsPrincipal(page);
  await expect.poll(() => dashboardStatisticsRequests.length).toBeGreaterThan(0);
  expect(dailyAttendanceRequests).toHaveLength(0);
});

test('parent home uses scoped dashboard attendance summary', async ({ page }) => {
  const dailyAttendanceRequests = [];
  const dashboardSummaryRequests = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/v1/attendance/daily')) dailyAttendanceRequests.push(request.url());
    if (request.url().includes('/api/v1/attendance/dashboard-summary')) dashboardSummaryRequests.push(request.url());
  });

  await loginAs(page, 'parent1@test.com');
  await expect.poll(() => dashboardSummaryRequests.length).toBeGreaterThan(0);
  expect(dailyAttendanceRequests).toHaveLength(0);
});

test('browser product time utility keeps Seoul timezone semantics', async ({ page }) => {
  await page.goto('/login');

  const result = await page.evaluate(() => ({
    dateInput: window.AppTime.todayInputValue(new Date('2026-08-14T15:30:00Z')),
    parsedTimestamp: window.AppTime.parse('2026-08-14T23:30:00').toISOString()
  }));

  expect(result.dateInput).toBe('2026-08-15');
  expect(result.parsedTimestamp).toBe('2026-08-14T14:30:00.000Z');
});

test('principal can scan the dashboard action queue and outbox timeline', async ({ page }) => {
  await loginAsPrincipal(page);

  await page.goto('/dashboard');
  await expect(page.getByRole('heading', { name: '오늘의 운영' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '오늘 바로 처리할 업무' })).toBeVisible();
  await expect(page.getByRole('link', { name: /출결 기록 확인/ })).toBeVisible();
  await expect(page.locator('#dashboardStatus')).toContainText('갱신', { timeout: 10_000 });

  await page.goto('/notification-outbox');
  await expect(page.getByRole('heading', { name: '전달 타임라인' })).toBeVisible();
  await expect(page.locator('#statusFilter')).toBeVisible();
  await expect(page.locator('#deadLetterTableBody tr').first()).toBeVisible();

  await page.goto('/attendance');
  await expect(page.getByRole('heading', { name: '오늘 출결' })).toBeVisible();
  await expect(page.locator('#attendanceStatus')).toContainText('명', { timeout: 10_000 });

  await page.goto('/kids');
  await expect(page.getByRole('heading', { name: '원생 관리' })).toBeVisible();
  await page.getByRole('button', { name: '검색', exact: true }).click();
  await expect(page.locator('#kid-summary')).toContainText('명', { timeout: 10_000 });

  await page.goto('/applications/pending');
  await expect(page.getByRole('heading', { name: '지원/승인' })).toBeVisible();
  await expect(page.locator('#pending-content')).toBeVisible();

  await page.goto('/domain-audit-logs');
  await expect(page.getByRole('heading', { name: '업무 감사 로그' })).toBeVisible();
  await expect(page.locator('#auditStatus')).toContainText('건', { timeout: 10_000 });

  await page.goto('/notifications');
  await expect(page.getByRole('heading', { name: '알림 센터' })).toBeVisible();
  const notificationCenter = page.getByRole('main').locator('#notification-center-list');
  await expect(notificationCenter).toBeVisible();
  await expect(notificationCenter.locator('button button')).toHaveCount(0);

  await page.goto('/notepad');
  await expect(page.getByRole('heading', { name: '알림장' })).toBeVisible();
  await expect(page.locator('#notepadStatus')).toHaveText(/\d+개의 알림장을|조건에 맞는 알림장이 없습니다\./, { timeout: 10_000 });
});

test('mobile viewport keeps the login action usable', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/login');

  const loginButton = page.getByRole('button', { name: '로그인', exact: true });
  await expect(loginButton).toBeVisible();
  await expect(loginButton).toHaveCSS('min-height', '44px');
  await expect(page.locator('body')).toHaveJSProperty('scrollWidth', 390);
});

test('reduced-motion preference removes entrance motion from the home brief', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await loginAsPrincipal(page);

  await page.goto('/');
  const homeBrief = page.locator('.home-brief.section-enter');
  await expect(homeBrief).toBeVisible();
  const motionStyle = await homeBrief.evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      opacity: style.opacity,
      transform: style.transform,
      animationDurationMs: parseFloat(style.animationDuration) || 0
    };
  });
  expect(motionStyle.opacity).toBe('1');
  expect(motionStyle.transform).toMatch(/^(none|matrix\(1, 0, 0, 1, 0, 0\))$/);
  expect(motionStyle.animationDurationMs).toBeLessThanOrEqual(0.01);
});

test('parent can review request status on a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await loginAs(page, 'parent1@test.com');

  await page.goto('/attendance-requests');
  await expect(page.getByRole('heading', { name: '내 출결 변경 요청' })).toBeVisible();
  await expect(page.locator('#kidId[required]')).toBeVisible();
  await expect(page.locator('#requestDate[required]')).toBeVisible();
  await expect(page.locator('#requestStatus[required]')).toBeVisible();
  await expect(page.locator('#requestCountValue')).toHaveText(/\d+|-/);
  await expect(page.locator('body')).toHaveJSProperty('scrollWidth', 390);

  await page.goto('/notepad');
  await expect(page.getByRole('heading', { name: '알림장' })).toBeVisible();
  await expect(page.locator('#notepadStatus')).toHaveText(/\d+개의 알림장을|조건에 맞는 알림장이 없습니다\./, { timeout: 10_000 });
  await expect(page.locator('body')).toHaveJSProperty('scrollWidth', 390);
});
