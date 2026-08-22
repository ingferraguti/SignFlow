import { expect, test, type Page } from "@playwright/test";

const controlId = "MVP-E2E-ORU-001";
const externalIdentifier = "RPT-MVP-E2E-001";
const internalIdentifier = `HL7-LIS-DEMO-${controlId}`;
const signerId = "55555555-5555-5555-5555-555555555552";

type DeliveryDetail = {
  operation: { id: string; state: string; reportVersion: number };
  receipts: Array<{ id: string }>;
};

async function login(page: Page, username: string, password: string, target: string) {
  await page.goto(target);
  await expect(page).toHaveURL(/\/login/);
  await page.getByRole("button", { name: "Login with Keycloak" }).click();
  await page.getByLabel(/username/i).fill(username);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: /sign in/i }).click();
  await page.waitForURL((url) => !url.pathname.includes("protocol/openid-connect/auth") && !url.pathname.endsWith("/login"));
  await page.goto(target);
}

async function logout(page: Page) {
  await page.getByRole("button", { name: "Logout" }).click();
  await page.waitForURL(/\/login|\/realms\/signflow\/protocol\/openid-connect\/logout/);
  if (page.url().includes("/realms/signflow/")) {
    await page.getByRole("button", { name: "Logout" }).click();
  }
  await expect(page).toHaveURL(/\/login/);
}

function segment(name: string, fields: Record<number, string>) {
  const maximum = Math.max(...Object.keys(fields).map(Number));
  let result = name;
  let first = 1;
  if (name === "MSH") {
    result += `|${fields[2] ?? "^~\\&"}`;
    first = 3;
  }
  for (let field = first; field <= maximum; field += 1) result += `|${fields[field] ?? ""}`;
  return result + "\r";
}

function fictionalOru() {
  const pdf = Buffer.from("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n", "ascii").toString("base64");
  return segment("MSH", { 2: "^~\\&", 3: "LIS-DEMO", 4: "MVP-FACILITY", 5: "SIGNFLOW",
    7: "20260822120000", 9: "ORU^R01", 10: controlId, 11: "P", 12: "2.5" })
    + segment("PID", { 3: "PAT-MVP-E2E-001^^^MVP^MR", 5: "Collaudo^Mvp",
      7: "19900101", 8: "X", 19: "TSTMVP90A01H501Q" })
    + segment("PV1", { 10: "Medicina generale", 19: "EP-MVP-E2E-001" })
    + segment("OBR", { 3: externalIdentifier, 4: "REF^Referto MVP totalmente fittizio",
      7: "20260822115500", 22: "20260822120000", 24: "Medicina generale" })
    + segment("ZSF", { 1: "DMSLGN80A01H501U" })
    + segment("OBX", { 1: "1", 2: "ED", 3: "DOC^referto-mvp-fittizio.pdf",
      5: `^application/pdf^PDF^Base64^${pdf}`, 11: "F" });
}

test("MVP: ingestion, assignment, preview, review, mock signature, history, FSE and conservation", async ({ page }) => {
  test.setTimeout(180_000);
  const browserErrors: string[] = [];
  page.on("pageerror", (error) => browserErrors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error" && !message.text().includes("404 (Not Found)")) browserErrors.push(message.text());
  });

  // 1-5: full stack is started by the release runner; the administrator ingests and assigns a fictional Report.
  await login(page, "demo.admin", "local-admin-password", "/monitoraggio");
  const ingestion = await page.evaluate(async ({ raw, operationKey }) => {
    const response = await fetch("/api/backend/ingestion/hl7", {
      method: "POST",
      headers: {
        "content-type": "application/hl7-v2",
        "X-Idempotency-Key": operationKey,
        "X-Correlation-ID": "mvp-e2e-correlation-001",
        "X-Source-System": "LIS-DEMO",
      },
      body: raw,
    });
    return { status: response.status, body: await response.json() };
  }, { raw: fictionalOru(), operationKey: "mvp-e2e-ingestion-001" });
  expect(ingestion.status).toBe(201);
  expect(ingestion.body).toMatchObject({ status: "PROCESSED", reportState: "READY_TO_SIGN", idempotent: false });
  const reportId = String(ingestion.body.reportId);

  await page.goto("/referti");
  await page.getByRole("textbox", { name: "ID interno", exact: true }).fill(internalIdentifier);
  await page.getByRole("button", { name: "Cerca", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("row").filter({ hasText: internalIdentifier })
    .getByRole("button", { name: "Dettaglio", exact: true }).click();
  await expect(page.getByRole("heading", { name: `Dettaglio referto ${internalIdentifier}` })).toBeVisible();
  const workflow = page.getByRole("region", { name: "Workflow e assegnazione" });
  await workflow.getByRole("button", { name: "Rimuovi assegnazione" }).click();
  await expect(workflow.getByText("MISSING_SIGNER", { exact: true }).first()).toBeVisible();
  await workflow.getByLabel("Assegnazione").selectOption(signerId);
  await workflow.getByRole("button", { name: "Assegna firmatario" }).click();
  const readinessCall = page.waitForResponse((response) => response.url().endsWith("/workflow/evaluate-readiness")
    && response.request().method() === "POST");
  await workflow.getByRole("button", { name: "Verifica completezza" }).click();
  const readiness = await (await readinessCall).json() as { resultingVersion: number };
  await expect(workflow.getByText("READY_TO_SIGN", { exact: true }).first()).toBeVisible();

  const reviewAdmin = page.getByRole("region", { name: "Revisione, approvazione e controfirma" });
  await expect(reviewAdmin.getByText(new RegExp(`versione ${readiness.resultingVersion}\\.`))).toBeVisible();
  await reviewAdmin.getByRole("combobox", { name: "Approvatore", exact: true })
    .selectOption("55555555-5555-5555-5555-555555555554");
  await reviewAdmin.getByText("Separazione produttore/firmatario/approvatore").click();
  await reviewAdmin.getByRole("button", { name: "Salva regole di revisione" }).click();
  await expect(reviewAdmin.getByRole("status").filter({ hasText: "Regole di revisione aggiornate" })).toBeVisible();
  await logout(page);

  // 6-9: the assigned natural person searches, opens the PDF and requests independent review.
  await login(page, "demo.signer", "local-signer-password", "/firma/referti");
  expect((await page.request.get("/api/backend/admin/reports")).status()).toBe(403);
  await page.getByLabel("Ricerca semplice").fill(internalIdentifier);
  await page.getByRole("button", { name: "Cerca", exact: true }).click();
  const signerRow = page.getByRole("row").filter({ hasText: internalIdentifier });
  await expect(signerRow).toBeVisible();
  await signerRow.getByRole("link", { name: `Apri ${internalIdentifier}` }).click();
  await expect(page.getByRole("heading", { name: `Referto ${internalIdentifier}` })).toBeVisible();
  const previewCall = page.waitForResponse((response) => response.url().includes("/preview")
    && response.request().method() === "POST");
  await page.getByRole("button", { name: "Visualizza PDF" }).click();
  expect((await previewCall).status()).toBe(200);
  await expect(page.getByTitle(`PDF ${internalIdentifier}`)).toBeVisible();
  const reviewRequest = page.waitForResponse((response) => response.url().endsWith("/review/request"));
  await page.getByRole("button", { name: "Richiedi approvazione" }).click();
  expect((await reviewRequest).status()).toBe(200);
  await expect(page.getByText("Richiesta inviata: decisione in attesa.")).toBeVisible();
  await logout(page);

  // 10: a distinct approver records document view and approval.
  await login(page, "demo.approver", "local-approver-password", "/approvazioni");
  expect((await page.request.get("/api/backend/signer/reports")).status()).toBe(403);
  const queueItem = page.locator(".review-queue article").filter({ hasText: internalIdentifier });
  await expect(queueItem).toBeVisible();
  await queueItem.getByRole("link", { name: "Apri referto" }).click();
  await page.getByRole("button", { name: "Apri documento da revisionare" }).click();
  await expect(page.getByTitle("Documento in revisione")).toBeVisible();
  const approval = page.waitForResponse((response) => response.url().endsWith("/approve"));
  await page.getByRole("button", { name: "Approva referto" }).click();
  expect((await approval).status()).toBe(200);
  await expect(page.getByText("APPROVED", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("APPROVED", { exact: true }).last()).toBeVisible();
  await logout(page);

  // 11-12: signer opens a temporary provider session and obtains a non-legal mock result.
  await login(page, "demo.signer", "local-signer-password", `/firma/referti/${reportId}`);
  await page.getByLabel("Codice autorizzazione mock").fill("000000");
  await page.getByRole("button", { name: "Avvia sessione mock" }).click();
  await expect(page.getByText(/Sessione attiva fino/)).toBeVisible();
  const signatureCall = page.waitForResponse((response) => response.url().endsWith("/signatures/single"));
  await page.getByRole("button", { name: "Firma singola mock" }).click();
  expect((await signatureCall).status()).toBe(200);
  await expect(page.getByText(/Esito: COMPLETED/)).toBeVisible();
  await expect(page.getByText(/MOCK ONLY - attestazione di collaudo/)).toBeVisible();
  await expect(page.getByText("SIGNED", { exact: true }).first()).toBeVisible();
  await logout(page);

  // 13-15: administrator reconstructs history and completes both mock adapter workflows.
  await login(page, "demo.admin", "local-admin-password", "/integrazioni");
  const report = await page.evaluate(async (id) => {
    const response = await fetch(`/api/backend/admin/reports/${id}`);
    return response.json();
  }, reportId);
  await page.getByLabel("ID tecnico referto").fill(reportId);
  await page.getByLabel("Versione workflow").fill(String(report.workflowVersion));
  const fseSendCall = page.waitForResponse((response) => response.url().includes("/external-deliveries/fse/reports/"));
  await page.getByRole("button", { name: "Invia a FSE mock" }).click();
  const fseSent = await (await fseSendCall).json() as DeliveryDetail;
  expect(fseSent.operation.state, JSON.stringify(fseSent)).toBe("FSE_SENT");
  const fseReconcileCall = page.waitForResponse((response) => response.url().endsWith(`/${fseSent.operation.id}/reconcile`));
  await page.getByRole("button", { name: "Riconcilia esito", exact: true }).click();
  const fseAccepted = await (await fseReconcileCall).json() as DeliveryDetail;
  expect(fseAccepted.operation.state).toBe("FSE_ACCEPTED");
  await expect(page.getByText("FSE_ACCEPTED", { exact: true }).last()).toBeVisible();

  await page.getByLabel("Versione workflow").fill(String(fseAccepted.operation.reportVersion));
  const conservationSendCall = page.waitForResponse((response) => response.url().includes("/external-deliveries/conservation/reports/"));
  await page.getByRole("button", { name: "Invia in conservazione mock" }).click();
  const conservationSent = await (await conservationSendCall).json() as DeliveryDetail;
  expect(conservationSent.operation.state).toBe("CONSERVATION_SENT");
  const conservationReconcileCall = page.waitForResponse((response) => response.url().endsWith(`/${conservationSent.operation.id}/reconcile`));
  await page.getByRole("button", { name: "Riconcilia esito", exact: true }).click();
  const conservationAccepted = await (await conservationReconcileCall).json() as DeliveryDetail;
  expect(conservationAccepted.operation.state).toBe("CONSERVATION_ACCEPTED");
  expect(conservationAccepted.receipts.length).toBeGreaterThanOrEqual(2);
  await expect(page.getByRole("link", { name: /Scarica ricevuta/ }).first()).toBeVisible();

  await page.goto("/referti");
  await page.getByRole("textbox", { name: "ID interno", exact: true }).fill(internalIdentifier);
  await page.getByRole("button", { name: "Cerca", exact: true }).click();
  await page.getByRole("row").filter({ hasText: internalIdentifier })
    .getByRole("button", { name: "Dettaglio", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Timeline della pratica" })).toBeVisible();
  await expect(page.getByText("SIGNATURE_PROVIDER_OUTCOME", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("FSE_DELIVERY_STATE_CHANGED", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("CONSERVATION_DELIVERY_STATE_CHANGED", { exact: true }).first()).toBeVisible();

  await page.setViewportSize({ width: 390, height: 844 });
  const responsive = await page.evaluate(() => ({
    fits: document.documentElement.scrollWidth <= document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
    viewportWidth: document.documentElement.clientWidth,
    overflowing: [...document.querySelectorAll<HTMLElement>("body *")]
      .filter((element) => element.getBoundingClientRect().right > document.documentElement.clientWidth + 1)
      .filter((element) => {
        let ancestor = element.parentElement;
        while (ancestor && ancestor !== document.body) {
          if (["auto", "scroll", "hidden"].includes(getComputedStyle(ancestor).overflowX)) return false;
          ancestor = ancestor.parentElement;
        }
        return true;
      })
      .slice(0, 20)
      .map((element) => ({ tag: element.tagName, className: element.className,
        right: Math.round(element.getBoundingClientRect().right), width: Math.round(element.getBoundingClientRect().width),
        text: element.innerText?.slice(0, 80) })),
  }));
  expect(responsive.fits, JSON.stringify(responsive)).toBe(true);
  await page.setViewportSize({ width: 1280, height: 720 });
  expect(browserErrors).toEqual([]);

  // 16: final logout and protected-route verification.
  await logout(page);
  expect((await page.request.post("/api/backend/ingestion/hl7", {
    headers: { "content-type": "application/hl7-v2" }, data: "MSH|^~\\&",
  })).status()).toBe(401);
  await page.goto("/integrazioni");
  await expect(page).toHaveURL(/\/login/);
});
