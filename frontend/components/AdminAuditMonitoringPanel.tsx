"use client";

import { FormEvent, useEffect, useState } from "react";
import { applyAuditRetention, auditExportUrl, emptyAuditFilters, fetchAuditEvents, fetchAuditRetention,
  saveAuditRetention, type AuditFilters, type AuditPage, type AuditRetention } from "../lib/adminAudit";
import { useUiTexts } from "./UiTextProvider";

export function AdminAuditMonitoringPanel() {
  const { text } = useUiTexts();
  const [filters, setFilters] = useState<AuditFilters>(emptyAuditFilters);
  const [applied, setApplied] = useState<AuditFilters>(emptyAuditFilters);
  const [page, setPage] = useState<AuditPage>({ items: [], page: 0, size: 20, total: 0 });
  const [retention, setRetention] = useState<AuditRetention>();
  const [retentionDays, setRetentionDays] = useState(365);
  const [status, setStatus] = useState("");
  const [error, setError] = useState<string>();

  useEffect(() => {
    Promise.all([fetchAuditEvents(emptyAuditFilters()), fetchAuditRetention()])
      .then(([events, policy]) => { setPage(events); setRetention(policy); setRetentionDays(policy.retentionDays); })
      .catch((reason: Error) => setError(reason.message));
  }, []);

  async function search(event?: FormEvent, nextPage = 0, nextFilters = filters) {
    event?.preventDefault(); setError(undefined); setStatus("");
    try { setPage(await fetchAuditEvents(nextFilters, nextPage)); setApplied(nextFilters); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Ricerca audit non riuscita"); }
  }

  async function saveRetention(event: FormEvent) {
    event.preventDefault(); setError(undefined);
    try { const saved = await saveAuditRetention(retentionDays); setRetention(saved); setStatus("Policy di retention aggiornata"); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Salvataggio non riuscito"); }
  }

  async function applyRetentionNow() {
    setError(undefined);
    try { const result = await applyAuditRetention(); setStatus(`Retention applicata: ${result.deletedEvents} eventi scaduti rimossi`); await search(undefined, 0, applied); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Applicazione retention non riuscita"); }
  }

  return <div className="audit-layout">
    <section className="card audit-filter-card">
      <div className="section-title"><div><h1>{text("label.auditTitle")}</h1><p>Storico applicativo append-only, consultabile senza accedere ai log tecnici.</p></div><span className="status">{page.total} eventi</span></div>
      <form className="audit-filter-form" onSubmit={(event) => search(event)}>
        <label>Tipo evento<input value={filters.eventType} onChange={(e) => setFilters({ ...filters, eventType: e.target.value.toUpperCase() })} placeholder="es. REVIEW_APPROVED" /></label>
        <label>Utente o soggetto<input value={filters.actorId} onChange={(e) => setFilters({ ...filters, actorId: e.target.value })} /></label>
        <label>Correlation ID<input value={filters.correlationId} onChange={(e) => setFilters({ ...filters, correlationId: e.target.value })} /></label>
        <label>Entità<select value={filters.entityType} onChange={(e) => setFilters({ ...filters, entityType: e.target.value })}><option value="">Tutte</option><option>REPORT</option><option>DOCUMENT</option><option>SIGNATURE_BATCH</option><option>SIGNATURE_ATTEMPT</option><option>CONFIGURATION</option><option>SESSION</option></select></label>
        <label>ID entità<input value={filters.entityId} onChange={(e) => setFilters({ ...filters, entityId: e.target.value })} /></label>
        <label>Esito<select value={filters.outcome} onChange={(e) => setFilters({ ...filters, outcome: e.target.value })}><option value="">Tutti</option><option>SUCCESS</option><option>FAILURE</option><option>DENIED</option><option>PENDING</option></select></label>
        <label>Dal<input type="datetime-local" value={filters.occurredFrom} onChange={(e) => setFilters({ ...filters, occurredFrom: e.target.value })} /></label>
        <label>Al<input type="datetime-local" value={filters.occurredTo} onChange={(e) => setFilters({ ...filters, occurredTo: e.target.value })} /></label>
        <div className="form-actions"><button className="primary" type="submit">{text("button.searchAudit")}</button><button type="button" onClick={() => { const cleared = emptyAuditFilters(); setFilters(cleared); search(undefined, 0, cleared); }}>{text("button.resetFilters")}</button><a className="link-button primary" href={auditExportUrl(applied)}>{text("button.exportAudit")}</a></div>
      </form>
      {error ? <p className="inline-error" role="alert">{error}</p> : null}
      {status ? <p className="status" role="status">{status}</p> : null}
    </section>

    <section className="card audit-results-card"><div className="table-wrap"><table className="audit-table"><thead><tr><th>Quando</th><th>Evento</th><th>Soggetto</th><th>Entità</th><th>Esito</th><th>Correlation ID</th><th>Dettagli minimali</th></tr></thead><tbody>{page.items.map((item) => <tr key={item.id}><td>{formatDate(item.occurredAt)}</td><td><strong>{item.eventType}</strong>{item.reason ? <small>Motivo: {item.reason}</small> : null}</td><td>{item.actorId}<small>{item.actorType}</small></td><td>{item.entityType}<small>{item.entityId}</small></td><td><span className={`audit-outcome ${item.outcome.toLowerCase()}`}>{item.outcome}</span></td><td><code>{item.correlationId}</code></td><td>{Object.entries(item.metadata).map(([key, value]) => <small key={key}>{key}: {String(value ?? "—")}</small>)}</td></tr>)}</tbody></table></div>
      {!page.items.length ? <p className="empty-state">Nessun evento corrisponde ai filtri.</p> : null}
      <div className="pagination"><button disabled={page.page === 0} onClick={() => search(undefined, page.page - 1, applied)}>{text("button.previousPage")}</button><span>Pagina {page.page + 1}</span><button disabled={(page.page + 1) * page.size >= page.total} onClick={() => search(undefined, page.page + 1, applied)}>{text("button.nextPage")}</button></div>
    </section>

    <section className="card audit-retention-card"><div><h2>Retention audit</h2><p>La policy agisce solo sugli eventi scaduti; lo storico non è modificabile.</p></div><form onSubmit={saveRetention}><label>Giorni di conservazione<input type="number" min={30} max={3650} value={retentionDays} onChange={(e) => setRetentionDays(Number(e.target.value))} /></label><button className="primary" type="submit">{text("button.saveRetention")}</button><button type="button" onClick={applyRetentionNow}>{text("button.applyRetention")}</button></form>{retention ? <small>Ultimo aggiornamento: {formatDate(retention.updatedAt)} · {retention.updatedBy}</small> : null}</section>
  </div>;
}

function formatDate(value: string) { return new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "medium" }).format(new Date(value)); }
