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
