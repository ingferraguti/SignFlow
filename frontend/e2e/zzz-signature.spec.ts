import { expect, test, type Page } from "@playwright/test";

const reportId = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1";

async function login(page: Page) {
  await page.goto("/firma/referti/" + reportId);
  await expect(page).toHaveURL(/\/login/);
  await page.getByRole("button", { name: "Login with Keycloak" }).click();
  await page.getByLabel(/username/i).fill("demo.signer");
  await page.locator("#password").fill("local-signer-password");
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.goto("/firma/referti/" + reportId);
}

test("approved report crosses mock signature and exposes a non-legal outcome", async ({ page }) => {
  const browserErrors: string[] = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error" && !message.text().includes("404 (Not Found)")) browserErrors.push(message.text());
  });
  await login(page);
  const steps = page.locator(".workflow-steps");
  await expect(steps.getByText("Anteprima", { exact: true })).toBeVisible();
  await expect(steps.getByText("Revisione", { exact: true })).toBeVisible();
  await expect(steps.getByText("Firma", { exact: true })).toBeVisible();
  await expect(page.getByText("MOCK ONLY · NON È UNA FIRMA DIGITALE VALIDA")).toBeVisible();

  await page.getByLabel("Codice autorizzazione mock").fill("000000");
  const sessionCall = page.waitForResponse((response) =>
    response.url().endsWith("/api/backend/signer/signatures/provider-sessions")
      && response.request().method() === "POST");
  await page.getByRole("button", { name: "Avvia sessione mock" }).click();
  expect((await sessionCall).status()).toBe(200);
  await expect(page.getByText(/Sessione attiva fino/)).toBeVisible();

  const signatureCall = page.waitForResponse((response) =>
    response.url().endsWith("/api/backend/signer/signatures/single")
      && response.request().method() === "POST");
  await page.getByRole("button", { name: "Firma singola mock" }).click();
  expect((await signatureCall).status()).toBe(200);
  await expect(page.getByText(/Esito: COMPLETED/)).toBeVisible();
  await expect(page.getByText(/MOCK ONLY - attestazione di collaudo/)).toBeVisible();

  await page.getByRole("link", { name: "Apri riepilogo batch" }).click();
  await expect(page.getByRole("heading", { name: /Batch/ })).toBeVisible();
  await expect(page.getByText("COMPLETED", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("SUCCEEDED", { exact: true })).toBeVisible();
  browserErrors.length = 0;
  const desktop = await page.evaluate(() => ({
    overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    columns: getComputedStyle(document.querySelector(".batch-summary")!).gridTemplateColumns,
  }));
  expect(desktop.overflow).toBe(false); expect(desktop.columns.split(" ")).toHaveLength(4);

  await page.setViewportSize({ width: 390, height: 844 });
  const mobile = await page.evaluate(() => ({
    overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    columns: getComputedStyle(document.querySelector(".batch-summary")!).gridTemplateColumns,
    navigation: getComputedStyle(document.querySelector(".sidebar nav")!).display,
  }));
  expect(mobile.overflow).toBe(false); expect(mobile.columns.split(" ")).toHaveLength(2);
  expect(mobile.navigation).toBe("flex"); expect(browserErrors).toEqual([]);
});
