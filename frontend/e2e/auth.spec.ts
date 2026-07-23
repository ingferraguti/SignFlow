import { expect, test } from "@playwright/test";

test("protects route, logs in, shows current user, logs out, and protects route again", async ({ page }) => {
  await page.goto("/system");
  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole("heading", { name: "SignFlow login" })).toBeVisible();

  await page.getByRole("button", { name: "Login with Keycloak" }).click();
  await page.getByLabel(/username/i).fill("demo.admin");
  await page.locator("#password").fill("local-admin-password");
  await page.getByRole("button", { name: /sign in/i }).click();

  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByText("Signed in as")).toBeVisible();
  await expect(page.getByText("demo.admin")).toBeVisible();

  await page.goto("/system");
  await expect(page.getByRole("heading", { name: "System status" })).toBeVisible();
  await expect(page.getByText("Application:")).toBeVisible();

  await page.goto("/configurazione");
  await expect(page.getByRole("heading", { name: "Utenti" })).toBeVisible();
  await expect(page.getByText("demo.admin").first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Struttura organizzativa" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Partizioni" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Aziende" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Gruppi" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Configurazioni tecniche" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Sistemi eroganti" })).toBeVisible();
  await expect(page.getByText("LIS-DEMO")).toBeVisible();
  await page.getByLabel("Passthrough").check();
  await expect(page.getByText("Creazione CDA e passthrough non possono essere attivi contemporaneamente.")).toBeVisible();
  await page.getByRole("button", { name: "Provider di firma" }).click();
  await expect(page.getByText("MOCK-REMOTE")).toBeVisible();
  await page.getByRole("button", { name: "Account di firma" }).click();
  await expect(page.getByRole("cell", { name: "demo-signer", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Mappature FSE" }).click();
  await expect(page.getByText("PRESIDIO-DEMO")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Profilo admin · Testi e traduzioni" })).toBeVisible();

  await page.getByRole("button", { name: "Logout" }).click();
  await page.waitForURL(/\/login|localhost:8081\/realms\/signflow\/protocol\/openid-connect\/logout/);
  if (page.url().includes("localhost:8081")) {
    await expect(page.getByRole("heading", { name: "Logging out" })).toBeVisible();
    await page.getByRole("button", { name: "Logout" }).click();
  }

  await expect(page).toHaveURL(/\/login/);
  await page.goto("/system");
  await expect(page).toHaveURL(/\/login/);
});
