"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import { reportStates, type ReportPage } from "../lib/adminReports";
import { createFilteredBatch, createManualBatch } from "../lib/signaturePortal";
import { emptySignerFilters, fetchSignerReports, type SignerFilters } from "../lib/signerPortal";
import { PortalError } from "./SignerHomePanel";
import { useUiTexts } from "./UiTextProvider";

export function SignerReportsPanel() {
  const { text } = useUiTexts(); const router = useRouter();
  const [advanced, setAdvanced] = useState(false);
  const [filters, setFilters] = useState<SignerFilters>(emptySignerFilters);
  const [applied, setApplied] = useState<SignerFilters>(emptySignerFilters);
  const [result, setResult] = useState<ReportPage>({ items: [], page: 0, size: 20, total: 0 });
  const [selected, setSelected] = useState<string[]>([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState<Error>();
  async function search(event?: FormEvent, page = 0, next = filters) {
    event?.preventDefault(); setLoading(true); setError(undefined);
    try { setResult(await fetchSignerReports(next, page)); setApplied(next); setSelected([]); }
    catch (reason) { setError(reason instanceof Error ? reason : new Error("Ricerca non riuscita")); }
    finally { setLoading(false); }
  }
  useEffect(() => {
    const initial = emptySignerFilters();
    fetchSignerReports(initial).then((page) => { setResult(page); setApplied(initial); })
      .catch((reason) => setError(reason instanceof Error ? reason : new Error("Ricerca non riuscita")))
      .finally(() => setLoading(false));
  }, []);
  async function createBatch(mode: "manual" | "filtered") {
    setLoading(true); setError(undefined);
    try {
      const batch = mode === "manual" ? await createManualBatch(selected)
        : await createFilteredBatch({ query: applied.query, patient: applied.patient,
          documentType: applied.documentType, department: applied.department });
      router.push("/firma/batch/" + batch.id);
    } catch (reason) { setError(reason instanceof Error ? reason : new Error("Creazione batch non riuscita")); }
    finally { setLoading(false); }
  }
  if (error && !result.items.length) return <PortalError error={error} retry={() => search(undefined, result.page, applied)} />;
  return <div className="signer-layout"><section className="card signer-wide"><div className="section-title"><div><h1>I miei referti</h1><p>Vedi solo i referti assegnati a te o autorizzati per gruppo/partizione.</p></div><button type="button" onClick={() => setAdvanced(!advanced)}>{text(advanced ? "button.simpleSearch" : "button.advancedSearch")}</button></div>
    <form className="signer-search" onSubmit={(event) => search(event)}><label>Ricerca semplice<input placeholder="Paziente, ID, tipo o reparto" value={filters.query} onChange={(event) => setFilters({ ...filters, query: event.target.value })} /></label>
      {advanced ? <div className="advanced-search"><label>Paziente<input value={filters.patient} onChange={(e) => setFilters({ ...filters, patient: e.target.value })} /></label><label>Tipo referto<input value={filters.documentType} onChange={(e) => setFilters({ ...filters, documentType: e.target.value })} /></label><label>Reparto<input value={filters.department} onChange={(e) => setFilters({ ...filters, department: e.target.value })} /></label><label>Stato<select value={filters.state} onChange={(e) => setFilters({ ...filters, state: e.target.value })}><option value="">Tutti</option>{reportStates.map((state) => <option key={state}>{state}</option>)}</select></label><DateInput label="Referto dal" value={filters.producedFrom} set={(value) => setFilters({ ...filters, producedFrom: value })} /><DateInput label="Referto al" value={filters.producedTo} set={(value) => setFilters({ ...filters, producedTo: value })} /><DateInput label="Firma dal" value={filters.signedFrom} set={(value) => setFilters({ ...filters, signedFrom: value })} /><DateInput label="Firma al" value={filters.signedTo} set={(value) => setFilters({ ...filters, signedTo: value })} /></div> : null}
      <div className="form-actions"><button className="primary" type="submit">{text("button.search")}</button><button type="button" onClick={() => { const cleared = emptySignerFilters(); setFilters(cleared); search(undefined, 0, cleared); }}>{text("button.clearSearch")}</button></div></form>
      {error ? <p className="inline-error" role="alert">{error.message}</p> : null}</section>
    <section className="card signer-wide"><div className="section-title"><div><h2>Risultati</h2><p>{result.total} referti · {selected.length} selezionati</p></div>{loading ? <span className="loading-label">Caricamento…</span> : null}</div>
      <div className="batch-toolbar"><span>Solo i referti APPROVED possono entrare in un batch.</span><button disabled={loading || selected.length === 0} onClick={() => createBatch("manual")}>{text("button.createManualBatch")}</button><button className="primary" disabled={loading} onClick={() => createBatch("filtered")}>{text("button.signAllFiltered")}</button></div>
      {!loading && result.items.length === 0 ? <p className="empty-state">Nessun risultato. Modifica o azzera i criteri di ricerca.</p> : <div className="table-wrap"><table className="signer-table"><thead><tr><th>Selezione</th><th>Anteprima</th><th>Paziente</th><th>Tipo referto</th><th>Reparto</th><th>Stato</th><th>Data referto</th><th>Data firma</th></tr></thead><tbody>{result.items.map((report) => <tr key={report.id}><td><input aria-label={"Seleziona " + report.internalIdentifier} type="checkbox" disabled={report.state !== "APPROVED"} checked={selected.includes(report.id)} onChange={(e) => setSelected(e.target.checked ? [...selected, report.id] : selected.filter((id) => id !== report.id))} /></td><td><Link className="preview-link" aria-label={"Apri " + report.internalIdentifier} href={"/firma/referti/" + report.id}>Apri</Link></td><td><strong>{report.patientDisplayName}</strong><small>{report.internalIdentifier}</small></td><td>{report.documentType}</td><td>{report.department}</td><td><span className="state-badge">{report.state}</span></td><td>{formatDate(report.producedAt)}</td><td>{formatDate(report.signedAt)}</td></tr>)}</tbody></table></div>}
      <div className="pagination"><button disabled={loading || result.page === 0} onClick={() => search(undefined, result.page - 1, applied)}>{text("button.previousPage")}</button><span>Pagina {result.page + 1}</span><button disabled={loading || (result.page + 1) * result.size >= result.total} onClick={() => search(undefined, result.page + 1, applied)}>{text("button.nextPage")}</button></div></section></div>;
}

function DateInput({ label, value, set }: { label: string; value: string; set: (value: string) => void }) { return <label>{label}<input type="date" value={value} onChange={(e) => set(e.target.value)} /></label>; }
export function formatDate(value?: string) { return value ? new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—"; }
