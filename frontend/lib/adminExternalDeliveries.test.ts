import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fetchDeliveries, startDelivery } from "./adminExternalDeliveries";

describe("admin external-delivery client", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
    vi.stubGlobal("crypto", { randomUUID: () => "mvp-operation-key" });
  });

  afterEach(() => vi.unstubAllGlobals());

  it("serializes filters and pagination without sending empty values", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({
      items: [], page: 2, size: 10, total: 0,
    }), { status: 200, headers: { "content-type": "application/json" } }));

    await fetchDeliveries({ channel: "FSE", state: "", correlationId: "corr-demo", reportIdentifier: "" }, 2, 10);

    expect(fetch).toHaveBeenCalledWith(
      "/api/backend/admin/external-deliveries?page=2&size=10&channel=FSE&correlationId=corr-demo",
      { cache: "no-store" },
    );
  });

  it("starts an idempotent FSE command with the current workflow version", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ operation: { id: "operation-1" } }), {
      status: 200, headers: { "content-type": "application/json" },
    }));

    await startDelivery("FSE", "report-1", 7);

    expect(fetch).toHaveBeenCalledWith(
      "/api/backend/admin/external-deliveries/fse/reports/report-1",
      expect.objectContaining({
        cache: "no-store",
        method: "POST",
        body: JSON.stringify({ expectedVersion: 7, operationKey: "mvp-operation-key" }),
      }),
    );
  });

  it("surfaces the safe API error returned to the administrator", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ message: "Versione workflow non valida" }), {
      status: 409, headers: { "content-type": "application/json" },
    }));

    await expect(startDelivery("CONSERVATION", "report-1", 2))
      .rejects.toThrow("Versione workflow non valida");
  });
});
