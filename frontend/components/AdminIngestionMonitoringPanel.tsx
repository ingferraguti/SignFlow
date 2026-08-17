"use client";

import { FormEvent, useEffect, useState } from "react";
import { emptyIngestionFilters, fetchIngestionMessage, fetchIngestionMessages, fetchMissingSignerReports,
  type IngestionFilters, type IngestionMessageDetail, type IngestionMessagePage, type MissingSignerPage } from "../lib/adminIngestion";
import { useUiTexts } from "./UiTextProvider";

export function AdminIngestionMonitoringPanel() {
  const { text } = useUiTexts();
  const [filters, setFilters] = useState<IngestionFilters>(emptyIngestionFilters);
  const [applied, setApplied] = useState<IngestionFilters>(emptyIngestionFilters);
  const [page, setPage] = useState<IngestionMessagePage>({ items: [], page: 0, size: 20, total: 0 });
  const [missing, setMissing] = useState<MissingSignerPage>({ items: [], page: 0, size: 20, total: 0 });
  const [detail, setDetail] = useState<IngestionMessageDetail>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    Promise.all([fetchIngestionMessages(emptyIngestionFilters()), fetchMissingSignerReports()])
      .then(([messages, reports]) => { setPage(messages); setMissing(reports); })
      .catch((reason: Error) => setError(reason.message));
  }, []);

  async function search(event?: FormEvent, nextPage = 0, nextFilters = filters) {
    event?.preventDefault(); setError(undefined); setDetail(undefined);
    try { setPage(await fetchIngestionMessages(nextFilters, nextPage)); setApplied(nextFilters); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Ricerca messaggi non riuscita"); }
  }
  async function open(id: string) {
    setError(undefined);
    try { setDetail(await fetchIngestionMessage(id)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Dettaglio non disponibile"); }
  }

  return <div className="ingestion-layout">
    <section className="card audit-filter-card">
      <div className="section-title"><div><h1>{text("label.ingestionMonitoringTitle")}</h1><p>Messaggi ORU/MDM, scarti e passaggi della pipeline guidata dal sistema erogante.</p></div><span className="status">{page.total} messaggi</span></div>
      <form className="audit-filter-form" onSubmit={(event) => search(event)}>
        <label>Stato<select value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value })}><option value="">Tutti</option><option>PROCESSED</option><option>DISCARDED</option><option>RECEIVED</option></select></label>
        <label>Sistema erogante<input value={filters.sourceSystemCode} onChange={(e) => setFilters({ ...filters, sourceSystemCode: e.target.value.toUpperCase() })} /></label>
        <label>Tipo messaggio<input value={filters.messageType} onChange={(e) => setFilters({ ...filters, messageType: e.target.value.toUpperCase() })} placeholder="ORU o MDM" /></label>
        <label>Correlation ID<input value={filters.correlationId} onChange={(e) => setFilters({ ...filters, correlationId: e.target.value })} /></label>
        <label>Ricevuto dal<input type="datetime-local" value={filters.receivedFrom} onChange={(e) => setFilters({ ...filters, receivedFrom: e.target.value })} /></label>
        <label>Ricevuto al<input type="datetime-local" value={filters.receivedTo} onChange={(e) => setFilters({ ...filters, receivedTo: e.target.value })} /></label>
        <div className="form-actions"><button className="primary" type="submit">{text("button.searchIngestion")}</button><button type="button" onClick={() => { const cleared = emptyIngestionFilters(); setFilters(cleared); search(undefined, 0, cleared); }}>{text("button.resetFilters")}</button></div>
      </form>
      {error ? <p className="inline-error" role="alert">{error}</p> : null}
    </section>

    <section className="card audit-results-card"><div className="table-wrap"><table className="ingestion-table"><thead><tr><th>Ricezione</th><th>Messaggio</th><th>Sistema</th><th>Stato</th><th>Correlation ID</th><th>Referto</th><th>Errore</th><th>Azione</th></tr></thead><tbody>{page.items.map((item) => <tr key={item.id}><td>{formatDate(item.receivedAt)}<small>{item.transport}</small></td><td>{item.messageType ?? "—"}^{item.triggerEvent ?? "—"}<small>{item.controlId ?? "—"}</small></td><td>{item.sourceSystemCode ?? "Non riconosciuto"}</td><td><span className={`audit-outcome ${item.status === "PROCESSED" ? "success" : item.status === "DISCARDED" ? "failure" : "pending"}`}>{item.status}</span></td><td><code>{item.correlationId}</code></td><td>{item.reportId ?? "—"}</td><td>{item.errorCode ?? "—"}<small>{item.errorMessage ?? ""}</small></td><td><button onClick={() => open(item.id)}>{text("button.openIngestionMessage")}</button></td></tr>)}</tbody></table></div>
      {!page.items.length ? <p className="empty-state">Nessun messaggio corrisponde ai filtri.</p> : null}
      <div className="pagination"><button disabled={page.page === 0} onClick={() => search(undefined, page.page - 1, applied)}>{text("button.previousPage")}</button><span>Pagina {page.page + 1}</span><button disabled={(page.page + 1) * page.size >= page.total} onClick={() => search(undefined, page.page + 1, applied)}>{text("button.nextPage")}</button></div>
    </section>

    {detail ? <section className="card ingestion-detail" aria-label="Dettaglio messaggio HL7"><div className="section-title"><div><h2>Dettaglio {detail.message.messageType ?? "HL7"}^{detail.message.triggerEvent ?? "—"}</h2><p>Il payload è sempre mascherato; il raw completo resta nell’object storage privato.</p></div><button onClick={() => setDetail(undefined)}>{text("button.closeIngestionMessage")}</button></div><dl className="detail-grid"><Detail label="Stato" value={detail.message.status} /><Detail label="Versione HL7" value={detail.hl7Version} /><Detail label="Hash raw" value={detail.payloadSha256} /><Detail label="Dimensione" value={`${detail.rawSizeBytes} B`} /><Detail label="Retention raw" value={formatDate(detail.rawRetentionUntil)} /><Detail label="CDA builder" value={yesNo(detail.cdaBuilderCalled)} /><Detail label="Normalizer" value={yesNo(detail.documentNormalizerCalled)} /><Detail label="Converter PDF/A-3" value={yesNo(detail.pdfA3ConverterCalled)} /><Detail label="Passthrough" value={yesNo(detail.passthroughApplied)} /><Detail label="Duplicato di" value={detail.duplicateOfId} /></dl><h3>Passaggi pipeline</h3><div className="badge-list">{detail.pipelineSteps.map((step) => <span className="status" key={step}>{step}</span>)}</div><h3>Anteprima raw mascherata</h3><pre className="masked-hl7">{detail.maskedRawPreview}</pre></section> : null}

    <section className="card audit-results-card"><div className="section-title"><div><h2>Referti senza firmatario</h2><p>Coda operativa alimentata dalla pipeline.</p></div><span className="status">{missing.total} referti</span></div><div className="table-wrap"><table><thead><tr><th>ID interno</th><th>Sistema</th><th>Tipo</th><th>Reparto</th><th>Produzione</th></tr></thead><tbody>{missing.items.map((item) => <tr key={item.reportId}><td>{item.internalIdentifier}</td><td>{item.sourceSystemCode}</td><td>{item.documentType}</td><td>{item.department}</td><td>{formatDate(item.producedAt)}</td></tr>)}</tbody></table></div></section>
  </div>;
}

function Detail({ label, value }: { label: string; value?: string }) { return <div><dt>{label}</dt><dd>{value || "—"}</dd></div>; }
function yesNo(value: boolean) { return value ? "Sì" : "No"; }
function formatDate(value: string) { return new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "medium" }).format(new Date(value)); }
