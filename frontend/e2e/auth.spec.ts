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
  await expect(page.getByRole("cell", { name: "Firma remota demo principale", exact: true })).toBeVisible();
  await page.getByRole("button", { name: /Mapping presidi FSE|Mappature FSE/ }).click();
  await expect(page.getByText("PRESIDIO-DEMO")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Profilo admin · Testi e traduzioni" })).toBeVisible();

  await expect(page.getByLabel("Bottone workflow: assegna firmatario")).toHaveValue("Assegna firmatario");
  await expect(page.getByLabel("Bottone audit: cerca eventi")).toHaveValue("Cerca eventi");

  await page.goto("/referti");
  await expect(page.getByRole("heading", { name: "Pratiche e referti" })).toBeVisible();
  await expect(page.locator("tbody tr")).toHaveCount(10);
  await page.getByRole("textbox", { name: "ID interno", exact: true }).fill("RPT-INT-001");
  await expect(page.getByLabel("Paziente")).toBeDisabled();
  await page.getByRole("button", { name: "Cerca" }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("button", { name: "Dettaglio" }).click();
  await expect(page.getByRole("heading", { name: "Dettaglio referto RPT-INT-001" })).toBeVisible();
  await expect(page.getByText("PRACTICE-DEMO-001").first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Documenti clinici e allegati" })).toBeVisible();
  await expect(page.getByText("referto-dimostrativo.pdf")).toBeVisible();
  await page.getByLabel("File PDF").setInputFiles({
    name: "e2e-allegato.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"),
  });
  await page.getByRole("button", { name: "Carica PDF" }).click();
  await expect(page.getByText(/Versione \d+ caricata e verificata/)).toBeVisible();
  const documentRow = page.getByRole("row").filter({ hasText: "e2e-allegato.pdf" }).first();
  await expect(documentRow.getByText("ACTIVE")).toBeVisible();
  await documentRow.getByRole("button", { name: "Anteprima" }).click();
  await expect(page.getByTitle("Anteprima e2e-allegato.pdf")).toBeVisible();
  const download = page.waitForEvent("download");
  await documentRow.getByRole("button", { name: "Scarica" }).click();
  expect((await download).suggestedFilename()).toBe("e2e-allegato.pdf");

  await page.getByRole("button", { name: "Chiudi dettaglio" }).click();
  await page.getByRole("textbox", { name: "ID interno", exact: true }).fill("RPT-INT-003");
  await page.getByRole("button", { name: "Cerca" }).click();
  const workflowReportRow = page.getByRole("row", { name: /RPT-INT-003/ });
  await expect(workflowReportRow).toBeVisible();
  await workflowReportRow.getByRole("button", { name: "Dettaglio" }).click();
  await expect(page.getByRole("heading", { name: "Dettaglio referto RPT-INT-003" })).toBeVisible();
  const workflow = page.getByRole("region", { name: "Workflow e assegnazione" });
  await expect(workflow).toBeVisible();
  await expect(workflow.getByText(/Versione \d+/)).toBeVisible();
  const clearSigner = workflow.getByRole("button", { name: "Rimuovi assegnazione" });
  if (await clearSigner.isEnabled()) {
    await clearSigner.click();
    await expect(workflow.getByRole("status").filter({ hasText: "Assegnazione rimossa" })).toBeVisible();
  }
  await workflow.getByLabel("Assegnazione").selectOption("55555555-5555-5555-5555-555555555552");
  await workflow.getByRole("button", { name: "Assegna firmatario" }).click();
  await expect(workflow.getByRole("status").filter({ hasText: "Firmatario assegnato" })).toBeVisible();
  await page.getByLabel("File PDF").setInputFiles({
    name: "workflow-referto-fittizio.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"),
  });
  await page.getByRole("button", { name: "Carica PDF" }).click();
  await expect(page.getByText(/Versione \d+ caricata e verificata/)).toBeVisible();
  const readinessCall = page.waitForResponse((response) => response.url().includes(
    "/api/backend/admin/reports/cccccccc-cccc-cccc-cccc-ccccccccccc3/workflow/evaluate-readiness")
    && response.request().method() === "POST");
  await workflow.getByRole("button", { name: "Verifica completezza" }).click();
  expect((await readinessCall).status()).toBe(200);
  await expect(workflow.getByRole("status").filter({ hasText: "Completezza verificata" })).toBeVisible();
  await expect(workflow.getByText("READY_TO_SIGN", { exact: true }).first()).toBeVisible();
  await expect(workflow.getByRole("button", { name: "Applica correzione" })).toBeDisabled();
  await workflow.getByRole("button", { name: "Rimuovi assegnazione" }).click();
  await expect(workflow.getByRole("status").filter({ hasText: "Assegnazione rimossa" })).toBeVisible();
  await expect(workflow.getByText("MISSING_SIGNER", { exact: true }).first()).toBeVisible();

  const auditPageCall = page.waitForResponse((response) => response.url().includes("/api/backend/admin/audit/events?")
    && response.request().method() === "GET");
  const ingestionPageCall = page.waitForResponse((response) => response.url().includes("/api/backend/admin/monitoring/messages?")
    && response.request().method() === "GET");
  await page.goto("/monitoraggio");
  expect((await auditPageCall).status()).toBe(200);
  expect((await ingestionPageCall).status()).toBe(200);
  await expect(page.getByRole("heading", { name: "Audit e monitoraggio" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Ingestion HL7 e pipeline documentale" })).toBeVisible();
  await expect(page.getByText("HL7-DEMO-ORU-001", { exact: true })).toBeVisible();
  await expect(page.getByText("HL7-DEMO-MDM-001", { exact: true })).toBeVisible();
  await expect(page.getByText("SOURCE_SYSTEM_NOT_FOUND", { exact: true })).toBeVisible();
  const ingestionDetailCall = page.waitForResponse((response) => response.url().includes("/api/backend/admin/monitoring/messages/")
    && response.request().method() === "GET");
  await page.getByRole("button", { name: "Apri dettaglio messaggio" }).first().click();
  expect((await ingestionDetailCall).status()).toBe(200);
  await expect(page.getByRole("heading", { name: /Dettaglio (ORU|MDM)/ })).toBeVisible();
  await expect(page.getByText("CONTENUTO MASCHERATO", { exact: false }).first()).toBeVisible();
  await page.getByRole("button", { name: "Chiudi dettaglio messaggio" }).click();
  await page.getByLabel("Tipo evento").fill("DOCUMENT_UPLOADED");
  const auditFilterCall = page.waitForResponse((response) => response.url().includes("eventType=DOCUMENT_UPLOADED"));
  await page.getByRole("button", { name: "Cerca eventi" }).click();
  expect((await auditFilterCall).status()).toBe(200);
  await expect(page.getByText("DOCUMENT_UPLOADED", { exact: true }).first()).toBeVisible();
  const auditDownload = page.waitForEvent("download");
  await page.getByRole("link", { name: "Esporta CSV" }).click();
  expect((await auditDownload).suggestedFilename()).toBe("signflow-audit.csv");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  await page.setViewportSize({ width: 1280, height: 720 });

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
