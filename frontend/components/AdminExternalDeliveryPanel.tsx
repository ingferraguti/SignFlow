"use client";

import { FormEvent, useEffect, useState } from "react";
import { emptyDeliveryFilters, fetchDeliveries, fetchDelivery, reconcileDelivery, retryDelivery, startDelivery,
  type DeliveryChannel, type DeliveryDetail, type DeliveryFilters, type DeliveryPage } from "../lib/adminExternalDeliveries";
import { useUiTexts } from "./UiTextProvider";

const states = ["FSE_VALIDATION_ERROR", "FSE_SENT", "FSE_ACCEPTED", "FSE_REJECTED", "TIMEOUT",
  "CONSERVATION_SENT", "CONSERVATION_ACCEPTED", "CONSERVATION_REJECTED"];

export function AdminExternalDeliveryPanel() {
  const { text } = useUiTexts();
  const [filters, setFilters] = useState<DeliveryFilters>(emptyDeliveryFilters);
  const [applied, setApplied] = useState<DeliveryFilters>(emptyDeliveryFilters);
  const [page, setPage] = useState<DeliveryPage>({ items: [], page: 0, size: 20, total: 0 });
  const [detail, setDetail] = useState<DeliveryDetail>();
  const [reportId, setReportId] = useState(""); const [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(true); const [error, setError] = useState<string>(); const [status, setStatus] = useState("");

  useEffect(() => {
    const initial = emptyDeliveryFilters();
    fetchDeliveries(initial).then((value) => { setPage(value); setApplied(initial); })
      .catch((reason) => setError(message(reason))).finally(() => setLoading(false));
  }, []);
  async function load(next = applied, nextPage = 0) {
    try { setPage(await fetchDeliveries(next, nextPage)); setApplied(next); setError(undefined); }
    catch (reason) { setError(message(reason)); }
  }
  async function search(event: FormEvent) { event.preventDefault(); setDetail(undefined); await load(filters); }
  async function open(id: string) { try { setDetail(await fetchDelivery(id)); setError(undefined); } catch (reason) { setError(message(reason)); } }
  async function start(channel: DeliveryChannel) {
    if (!reportId.trim()) { setError("Inserire l'ID tecnico del referto"); return; }
    try { const value = await startDelivery(channel, reportId.trim(), version); setDetail(value); setStatus(`${channel}: invio mock avviato`); await load(applied); }
    catch (reason) { setError(message(reason)); }
  }
  async function act(action: "retry" | "reconcile") {
    if (!detail) return;
    try { const value = action === "retry" ? await retryDelivery(detail.operation) : await reconcileDelivery(detail.operation);
      setDetail(value); setStatus(action === "retry" ? "Retry registrato" : "Riconciliazione registrata"); await load(applied); }
    catch (reason) { setError(message(reason)); }
  }

  return <div className="delivery-layout">
    <section className="card delivery-intro"><div className="section-title"><div><h1>{text("label.externalDeliveryTitle")}</h1>
      <p>Workflow locale con adapter mock. Nessun dato viene inviato a endpoint sanitari reali.</p></div><span className="status">{page.total} operazioni</span></div>
      <div className="mock-warning"><strong>AMBIENTE MOCK</strong><span>Ricevute e riferimenti non hanno valore legale né sanitario.</span></div>
    </section>
    <section className="card"><h2>Nuovo invio controllato</h2><div className="delivery-start-form">
      <label>ID tecnico referto<input value={reportId} onChange={(event) => setReportId(event.target.value)} placeholder="UUID del referto firmato" /></label>
      <label>Versione workflow<input type="number" min={0} value={version} onChange={(event) => setVersion(Number(event.target.value))} /></label>
      <button className="primary" onClick={() => start("FSE")}>{text("button.sendFse")}</button>
      <button onClick={() => start("CONSERVATION")}>{text("button.sendConservation")}</button></div>
      <small>La conservazione richiede un referto già accettato da FSE.</small></section>
    <section className="card"><form className="delivery-filters" onSubmit={search}>
      <label>Canale<select value={filters.channel} onChange={(e) => setFilters({ ...filters, channel: e.target.value })}><option value="">Tutti</option><option>FSE</option><option>CONSERVATION</option></select></label>
      <label>Stato<select value={filters.state} onChange={(e) => setFilters({ ...filters, state: e.target.value })}><option value="">Tutti</option>{states.map((state) => <option key={state}>{state}</option>)}</select></label>
      <label>Referto<input value={filters.reportIdentifier} onChange={(e) => setFilters({ ...filters, reportIdentifier: e.target.value })} /></label>
      <label>Correlation ID<input value={filters.correlationId} onChange={(e) => setFilters({ ...filters, correlationId: e.target.value })} /></label>
      <div className="form-actions"><button className="primary" type="submit">Cerca</button><button type="button" onClick={() => { const cleared = emptyDeliveryFilters(); setFilters(cleared); load(cleared); }}>Azzera</button><button type="button" onClick={() => load(applied)}>{text("button.refreshDeliveries")}</button></div>
    </form></section>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}{status ? <p className="status" role="status">{status}</p> : null}
    <section className="card"><div className="table-wrap"><table className="delivery-table"><thead><tr><th>Referto</th><th>Canale</th><th>Stato</th><th>Tentativi</th><th>Presidio</th><th>Ultimo aggiornamento</th><th></th></tr></thead>
      <tbody>{page.items.map((item) => <tr key={item.id}><td><strong>{item.reportIdentifier}</strong><small>{item.reportId}</small></td><td>{item.channel}</td><td><span className={`delivery-state ${tone(item.state)}`}>{item.state}</span>{item.errorCode ? <small>{item.errorCode}</small> : null}</td><td>{item.attemptCount}/{item.maxRetries}</td><td>{item.facilityName}<small>{item.facilityCode}</small></td><td>{formatDate(item.updatedAt)}</td><td><button onClick={() => open(item.id)}>Dettagli</button></td></tr>)}</tbody></table></div>
      {loading ? <p>Caricamento esiti…</p> : !page.items.length ? <p className="empty-state">Nessuna operazione trovata.</p> : null}
      <div className="pagination"><button disabled={page.page === 0} onClick={() => load(applied, page.page - 1)}>Precedente</button><span>Pagina {page.page + 1}</span><button disabled={(page.page + 1) * page.size >= page.total} onClick={() => load(applied, page.page + 1)}>Successiva</button></div>
    </section>
    {detail ? <DeliveryDetails detail={detail} retry={() => act("retry")} reconcile={() => act("reconcile")} text={text} /> : null}
  </div>;
}

function DeliveryDetails({ detail, retry, reconcile, text }: { detail: DeliveryDetail; retry: () => void; reconcile: () => void; text: (key: string) => string }) {
  const operation = detail.operation;
  return <section className="card delivery-detail"><div className="section-title"><div><h2>{operation.channel} · {operation.reportIdentifier}</h2><p><code>{operation.correlationId}</code></p></div><span className={`delivery-state ${tone(operation.state)}`}>{operation.state}</span></div>
    <dl className="detail-grid"><div><dt>Adapter</dt><dd>{operation.adapterCode}</dd></div><div><dt>Presidio</dt><dd>{operation.facilityName} ({operation.facilityCode})</dd></div><div><dt>Unità operativa</dt><dd>{operation.operatingUnit}</dd></div><div><dt>Tipo documento</dt><dd>{operation.documentTypeCode}</dd></div><div><dt>Riferimento remoto</dt><dd>{operation.remoteReference ?? "—"}</dd></div><div><dt>Errore</dt><dd>{operation.errorMessage ?? "—"}</dd></div></dl>
    <div className="form-actions">{detail.reconciliationAllowed ? <button className="primary" onClick={reconcile}>{text("button.reconcileDelivery")}</button> : null}{detail.retryAllowed ? <button onClick={retry}>{text("button.retryDelivery")}</button> : null}</div>
    <h3>Tentativi</h3><ol className="delivery-timeline">{detail.attempts.map((attempt) => <li key={attempt.id}><strong>{attempt.action} · {attempt.outcome}</strong><span>{formatDate(attempt.completedAt)} · tentativo {attempt.attemptNumber}</span>{attempt.errorMessage ? <small>{attempt.errorCode}: {attempt.errorMessage}</small> : null}</li>)}</ol>
    <h3>Ricevute in object storage</h3><div className="receipt-list">{detail.receipts.map((receipt) => <a className="link-button" href={receipt.downloadUrl} key={receipt.id}>{text("button.openDeliveryReceipt")} · {receipt.receiptType} · {receipt.sizeBytes} B</a>)}{!detail.receipts.length ? <p className="empty-state">Nessuna ricevuta disponibile.</p> : null}</div>
  </section>;
}
function message(reason: unknown) { return reason instanceof Error ? reason.message : "Operazione non riuscita"; }
function formatDate(value: string) { return new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "medium" }).format(new Date(value)); }
function tone(state: string) { return state.includes("ACCEPTED") ? "accepted" : state.includes("SENT") ? "pending" : "rejected"; }
