"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  emptyReportFilters, fetchReportDetail, fetchReports, fetchReportSourceSystems, reportStates,
  type ReportDetail, type ReportFilters, type ReportPage, type SourceSystemOption,
} from "../lib/adminReports";
import { useUiTexts } from "./UiTextProvider";

export function AdminReportsPanel() {
  const { text } = useUiTexts();
  const [filters, setFilters] = useState<ReportFilters>(emptyReportFilters);
  const [applied, setApplied] = useState<ReportFilters>(emptyReportFilters);
  const [result, setResult] = useState<ReportPage>({ items: [], page: 0, size: 20, total: 0 });
  const [sources, setSources] = useState<SourceSystemOption[]>([]);
  const [detail, setDetail] = useState<ReportDetail>();
  const [error, setError] = useState<string>();

  useEffect(() => {
    Promise.all([fetchReports(emptyReportFilters()), fetchReportSourceSystems()])
      .then(([page, loadedSources]) => { setResult(page); setSources(loadedSources); })
      .catch((reason: Error) => setError(reason.message));
  }, []);

  async function search(event?: FormEvent, nextPage = 0, nextFilters = filters) {
    event?.preventDefault(); setError(undefined); setDetail(undefined);
    try { setResult(await fetchReports(nextFilters, nextPage)); setApplied(nextFilters); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Ricerca non riuscita"); }
  }

  async function showDetail(id: string) {
    setError(undefined);
    try { setDetail(await fetchReportDetail(id)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Dettaglio non disponibile"); }
  }

  const exactLookup = filters.internalIdentifier || filters.externalIdentifier || filters.fseIdentifier;
  return <div className="reports-layout">
    <section className="card reports-search-card">
      <div className="section-title"><div><h1>Pratiche e referti</h1><p>{result.total} referti trovati</p></div></div>
      <form className="report-filter-form" onSubmit={(event) => search(event)}>
        <fieldset className="exact-identifiers"><legend>Identificativi puntuali</legend>
          <label>ID interno<input value={filters.internalIdentifier} onChange={(e) => setFilters({ ...filters, internalIdentifier: e.target.value })} /></label>
          <label>ID esterno<input value={filters.externalIdentifier} onChange={(e) => setFilters({ ...filters, externalIdentifier: e.target.value })} /></label>
          <label>ID FSE<input value={filters.fseIdentifier} onChange={(e) => setFilters({ ...filters, fseIdentifier: e.target.value })} /></label>
          <p className="form-hint">Precedenza: ID interno, poi esterno, poi FSE. Un ID puntuale ignora gli altri filtri.</p>
        </fieldset>
        <label>Paziente<input disabled={Boolean(exactLookup)} value={filters.patient} onChange={(e) => setFilters({ ...filters, patient: e.target.value })} /></label>
        <label>Firmatario<input disabled={Boolean(exactLookup)} value={filters.signer} onChange={(e) => setFilters({ ...filters, signer: e.target.value })} /></label>
        <label>CF firmatario<input disabled={Boolean(exactLookup)} value={filters.signerFiscalCode} onChange={(e) => setFilters({ ...filters, signerFiscalCode: e.target.value })} /></label>
        <label>Stato<select disabled={Boolean(exactLookup)} value={filters.state} onChange={(e) => setFilters({ ...filters, state: e.target.value })}><option value="">Tutti</option>{reportStates.map((state) => <option key={state}>{state}</option>)}</select></label>
        <label>Sistema erogante<select disabled={Boolean(exactLookup)} value={filters.sourceSystemId} onChange={(e) => setFilters({ ...filters, sourceSystemId: e.target.value })}><option value="">Tutti</option>{sources.map((source) => <option key={source.id} value={source.id}>{source.code}</option>)}</select></label>
        <label>Reparto<input disabled={Boolean(exactLookup)} value={filters.department} onChange={(e) => setFilters({ ...filters, department: e.target.value })} /></label>
        <DateRange label="Produzione" from={filters.producedFrom} to={filters.producedTo} disabled={Boolean(exactLookup)} onFrom={(value) => setFilters({ ...filters, producedFrom: value })} onTo={(value) => setFilters({ ...filters, producedTo: value })} />
        <DateRange label="Modifica" from={filters.modifiedFrom} to={filters.modifiedTo} disabled={Boolean(exactLookup)} onFrom={(value) => setFilters({ ...filters, modifiedFrom: value })} onTo={(value) => setFilters({ ...filters, modifiedTo: value })} />
        <DateRange label="Firma" from={filters.signedFrom} to={filters.signedTo} disabled={Boolean(exactLookup)} onFrom={(value) => setFilters({ ...filters, signedFrom: value })} onTo={(value) => setFilters({ ...filters, signedTo: value })} />
        <label>Ordina per<select value={filters.sortBy} onChange={(e) => setFilters({ ...filters, sortBy: e.target.value })}><option value="producedAt">Produzione</option><option value="modifiedAt">Modifica</option><option value="signedAt">Firma</option><option value="internalIdentifier">ID interno</option><option value="state">Stato</option></select></label>
        <label>Direzione<select value={filters.direction} onChange={(e) => setFilters({ ...filters, direction: e.target.value })}><option value="desc">Decrescente</option><option value="asc">Crescente</option></select></label>
        <div className="form-actions"><button className="primary" type="submit">{text("button.search")}</button><button type="button" onClick={() => { const cleared = emptyReportFilters(); setFilters(cleared); search(undefined, 0, cleared); }}>{text("button.resetFilters")}</button></div>
      </form>
      {error ? <p className="inline-error" role="alert">{error}</p> : null}
    </section>

    <section className="card reports-results-card"><div className="table-wrap"><table><thead><tr><th>ID interno</th><th>Pratica</th><th>Paziente</th><th>Firmatario</th><th>Sistema</th><th>Reparto</th><th>Stato</th><th>Produzione</th><th>Azione</th></tr></thead><tbody>{result.items.map((report) => <tr key={report.id}><td>{report.internalIdentifier}<small>{report.externalIdentifier}</small><small>{report.fseIdentifier}</small></td><td>{report.practiceIdentifier}</td><td>{report.patientDisplayName}<small>{report.patientIdentifier}</small></td><td>{report.signerUsername ?? "Non assegnato"}<small>{report.signerFiscalCode}</small></td><td>{report.sourceSystemCode}</td><td>{report.department}</td><td><span className="state-badge">{report.state}</span></td><td>{formatDate(report.producedAt)}</td><td><button onClick={() => showDetail(report.id)}>{text("button.viewDetails")}</button></td></tr>)}</tbody></table></div>
      <div className="pagination"><button disabled={result.page === 0} onClick={() => search(undefined, result.page - 1, applied)}>{text("button.previousPage")}</button><span>Pagina {result.page + 1}</span><button disabled={(result.page + 1) * result.size >= result.total} onClick={() => search(undefined, result.page + 1, applied)}>{text("button.nextPage")}</button></div>
    </section>
    {detail ? <ReportDetailPanel detail={detail} close={() => setDetail(undefined)} /> : null}
  </div>;
}

function DateRange({ label, from, to, disabled, onFrom, onTo }: { label: string; from: string; to: string; disabled: boolean; onFrom: (value: string) => void; onTo: (value: string) => void }) {
  return <fieldset className="date-range"><legend>{label}</legend><label>Da<input type="date" disabled={disabled} value={from} onChange={(e) => onFrom(e.target.value)} /></label><label>A<input type="date" disabled={disabled} value={to} onChange={(e) => onTo(e.target.value)} /></label></fieldset>;
}

function ReportDetailPanel({ detail, close }: { detail: ReportDetail; close: () => void }) {
  const { text } = useUiTexts();
  return <section className="card report-detail"><div className="section-title"><div><h2>Dettaglio referto {detail.internalIdentifier}</h2><p>Pratica {detail.practice.practiceIdentifier}</p></div><button onClick={close}>{text("button.closeDetails")}</button></div>
    <dl className="detail-grid"><Detail label="ID esterno" value={detail.externalIdentifier} /><Detail label="ID FSE" value={detail.fseIdentifier} /><Detail label="Stato" value={detail.state} /><Detail label="Tipo documento" value={detail.documentType} /><Detail label="Reparto" value={detail.department} /><Detail label="Sistema erogante" value={detail.sourceSystemCode} /><Detail label="Paziente" value={`${detail.patient.lastName} ${detail.patient.firstName}`} /><Detail label="ID paziente" value={detail.patient.patientIdentifier} /><Detail label="CF paziente" value={detail.patient.fiscalCode} /><Detail label="Firmatario" value={detail.signerUsername ?? "Non assegnato"} /><Detail label="CF firmatario" value={detail.signerFiscalCode} /><Detail label="Produzione" value={formatDate(detail.producedAt)} /><Detail label="Modifica" value={formatDate(detail.modifiedAt)} /><Detail label="Firma" value={formatDate(detail.signedAt)} /></dl>
    <h3>Flag tecnici</h3><div className="flag-list">{[["PDF/A3", detail.pdfA3Conversion], ["Firma visibile", detail.visibleSignature], ["Firma multipla", detail.multipleSignature], ["Invio non firmato", detail.sendUnsigned], ["Creazione CDA", detail.createCda], ["Passthrough", detail.passthrough]].map(([label, value]) => <span key={String(label)} className={value ? "enabled" : ""}>{label}: {value ? "Sì" : "No"}</span>)}</div>
  </section>;
}

function Detail({ label, value }: { label: string; value?: string }) { return <div><dt>{label}</dt><dd>{value || "—"}</dd></div>; }
function formatDate(value?: string) { return value ? new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—"; }
