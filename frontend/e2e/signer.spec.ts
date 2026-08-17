import { expect, test } from "@playwright/test";

test("signer searches only authorized reports, opens PDF and uses portal pages", async ({ page }) => {
  const browserErrors: string[] = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));
  page.on("console", (message) => { if (message.type() === "error" && !message.text().includes("404 (Not Found)")) browserErrors.push(message.text()); });
  await page.goto("/firma");
  await expect(page).toHaveURL(/\/login/);
  await page.getByRole("button", { name: "Login with Keycloak" }).click();
  await page.getByLabel(/username/i).fill("demo.signer");
  await page.locator("#password").fill("local-signer-password");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByText("Signed in as")).toBeVisible();
  await page.waitForLoadState("networkidle");
  await page.goto("/firma");

  await expect(page.getByRole("heading", { name: "Home firmatario" })).toBeVisible();
  const homeTotal = Number(await page.getByText("Referti visibili").locator("..").locator("strong").textContent());
  expect(homeTotal).toBeGreaterThanOrEqual(7);
  await expect(page.getByRole("link", { name: "I miei referti" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Configurazione" })).toHaveCount(0);

  const reportsCall = page.waitForResponse((response) => response.url().includes("/api/backend/signer/reports?") && response.request().method() === "GET");
  await page.goto("/firma/referti");
  const reportsResponse = await reportsCall;
  expect(reportsResponse.status()).toBe(200);
  const reportsPage = await reportsResponse.json();
  expect(reportsPage.total).toBe(homeTotal);
  await expect(page.getByRole("heading", { name: "I miei referti" })).toBeVisible();
  await expect(page.locator("tbody tr")).toHaveCount(reportsPage.total);
  await expect(page.getByText("RPT-INT-006")).toHaveCount(0);
  await expect(page.getByText("RPT-INT-005")).toHaveCount(0);

  await page.getByPlaceholder("Paziente, ID, tipo o reparto").fill("RPT-INT-002");
  await page.getByRole("button", { name: "Cerca", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("link", { name: "Apri RPT-INT-002" }).click();
  await expect(page.getByRole("heading", { name: "Referto RPT-INT-002" })).toBeVisible();
  await expect(page.getByText("referto-pronto-fittizio.pdf")).toBeVisible();
  await page.getByRole("button", { name: "Visualizza PDF" }).click();
  await expect(page.getByTitle("PDF RPT-INT-002")).toBeVisible();
  await expect(page.getByText("PREVIEWED", { exact: true })).toBeVisible();

  await page.goto("/firma/referti/cccccccc-cccc-cccc-cccc-ccccccccccc5");
  await expect(page.getByRole("heading", { name: "Servizio non disponibile" })).toBeVisible();

  await page.goto("/firma/referti/cccccccc-cccc-cccc-cccc-ccccccccccc4");
  await expect(page.getByText("Documento non disponibile.")).toBeVisible();
  await expect(page.getByText("Referto incompleto.")).toBeVisible();

  await page.goto("/firma/stati");
  await expect(page.getByRole("heading", { name: "Legenda stati" })).toBeVisible();
  await expect(page.getByText("CONSERVATION_ACCEPTED")).toBeVisible();
  await page.goto("/firma/profilo");
  await expect(page.getByRole("heading", { name: "Profilo utente" })).toBeVisible();
  await expect(page.getByText("LOCAL-SIGNERS")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Account di accesso collegati" })).toBeVisible();
  await expect(page.getByText("demo.signer.alt")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Firme digitali disponibili" })).toBeVisible();
  await expect(page.getByLabel("Firma digitale preferita")).toBeVisible();
  await page.goto("/firma/informazioni");
  await expect(page.getByRole("heading", { name: "Informazioni" })).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/firma/referti");
  await expect(page.getByRole("heading", { name: "I miei referti" })).toBeVisible();
  const responsive = await page.evaluate(() => ({
    overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    navigation: getComputedStyle(document.querySelector(".sidebar nav")!).display,
    tableOverflow: getComputedStyle(document.querySelector(".table-wrap")!).overflowX,
  }));
  expect(responsive).toEqual({ overflow: false, navigation: "flex", tableOverflow: "auto" });
  expect(browserErrors).toEqual([]);

  await page.getByRole("button", { name: "Logout" }).click();
  await page.waitForURL(/\/login|localhost:8081\/realms\/signflow\/protocol\/openid-connect\/logout/);
  if (page.url().includes("localhost:8081")) await page.getByRole("button", { name: "Logout" }).click();
  await expect(page).toHaveURL(/\/login/);
});
