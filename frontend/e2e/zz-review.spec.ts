import { expect, test, type Page } from "@playwright/test";

const reportId = "cccccccc-cccc-cccc-cccc-ccccccccccc2";

async function login(page: Page, username: string, password: string, target: string) {
  await page.goto(target);
  await expect(page).toHaveURL(/\/login/);
  await page.getByRole("button", { name: "Login with Keycloak" }).click();
  await page.getByLabel(/username/i).fill(username);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.goto(target);
}

async function logout(page: Page) {
  await page.getByRole("button", { name: "Logout" }).click();
  await page.waitForURL(/\/login|localhost:8081\/realms\/signflow\/protocol\/openid-connect\/logout/);
  if (page.url().includes("localhost:8081")) await page.getByRole("button", { name: "Logout" }).click();
  await expect(page).toHaveURL(/\/login/);
}

test("signer requests an independent review and approver reaches APPROVED", async ({ page }) => {
  const browserErrors: string[] = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));
  page.on("console", (message) => { if (message.type() === "error" && !message.text().includes("404 (Not Found)")) browserErrors.push(message.text()); });

  await login(page, "demo.signer", "local-signer-password", `/firma/referti/${reportId}`);
  const steps = page.locator(".workflow-steps");
  await expect(steps.getByText("Anteprima", { exact: true })).toBeVisible();
  await expect(steps.getByText("Revisione", { exact: true })).toBeVisible();
  await expect(steps.getByText("Firma", { exact: true })).toBeVisible();
  const requestButton = page.getByRole("button", { name: "Richiedi approvazione" });
  if (await requestButton.isEnabled()) {
    const requestCall = page.waitForResponse((response) => response.url().includes(`/api/backend/signer/reports/${reportId}/review/request`) && response.request().method() === "POST");
    await requestButton.click(); expect((await requestCall).status()).toBe(200);
  }
  await expect(page.getByText("Richiesta inviata: decisione in attesa.")).toBeVisible();
  await logout(page);

  await login(page, "demo.approver", "local-approver-password", "/approvazioni");
  await expect(page.getByRole("heading", { name: "Revisioni assegnate" })).toBeVisible();
  browserErrors.length = 0;
  await expect(page.getByText("RPT-INT-002")).toBeVisible();
  await page.getByRole("link", { name: "Apri referto" }).first().click();
  await expect(page.getByRole("heading", { name: "Revisione RPT-INT-002" })).toBeVisible();
  const openCall = page.waitForResponse((response) => response.url().includes("/preview") && response.request().method() === "POST");
  await page.getByRole("button", { name: "Apri documento da revisionare" }).click();
  expect((await openCall).status()).toBe(200);
  await expect(page.getByTitle("Documento in revisione")).toBeVisible();
  const approveCall = page.waitForResponse((response) => response.url().endsWith("/approve") && response.request().method() === "POST");
  await page.getByRole("button", { name: "Approva referto" }).click();
  expect((await approveCall).status()).toBe(200);
  await expect(page.getByText("Referto approvato")).toBeVisible();
  await expect(page.getByText("APPROVED", { exact: true }).first()).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole("heading", { name: "Revisione RPT-INT-002" })).toBeVisible();
  const responsive = await page.evaluate(() => ({
    overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    steps: getComputedStyle(document.querySelector(".workflow-steps")!).gridTemplateColumns,
    navigation: getComputedStyle(document.querySelector(".sidebar nav")!).display,
  }));
  expect(responsive.overflow).toBe(false); expect(responsive.navigation).toBe("flex");
  expect(responsive.steps.split(" ")).toHaveLength(1);
  expect(browserErrors).toEqual([]);
  await logout(page);

  await login(page, "demo.admin", "local-admin-password", "/referti");
  await page.getByRole("textbox", { name: "ID interno", exact: true }).fill("RPT-INT-002");
  await page.getByRole("button", { name: "Cerca" }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("button", { name: "Dettaglio" }).click();
  const reviewAdmin = page.getByRole("region", { name: "Revisione, approvazione e controfirma" });
  await reviewAdmin.getByLabel("Motivazione").fill("Ripristino del dato demo dopo verifica E2E");
  await reviewAdmin.getByRole("button", { name: "Ritorna al passaggio precedente" }).click();
  await expect(reviewAdmin.getByRole("status").filter({ hasText: "Ritorno registrato" })).toBeVisible();
  await reviewAdmin.getByRole("button", { name: "Ritorna al passaggio precedente" }).click();
  await expect(reviewAdmin.getByText("PREVIEWED", { exact: true }).first()).toBeVisible();
  await logout(page);
});
