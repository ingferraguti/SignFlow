"use client";

import { FormEvent, useState } from "react";
import {
  applyAdministrativeCorrection, assignWorkflowSigner, evaluateWorkflowReadiness,
  type ReportDetail, type ReportState, type ReportWorkflow, type WorkflowSigner,
} from "../lib/adminReports";
import { useUiTexts } from "./UiTextProvider";

export function AdminReportWorkflowPanel({ detail, workflow, signers, reload }: {
  detail: ReportDetail; workflow: ReportWorkflow; signers: WorkflowSigner[]; reload: () => Promise<void>;
}) {
  const { text } = useUiTexts();
  const [selectedSigner, setSelectedSigner] = useState(detail.assignedSignerId ?? "");
  const [correctionTarget, setCorrectionTarget] = useState<ReportState>("INCOMPLETE");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [status, setStatus] = useState<string>();

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true); setError(undefined); setStatus(undefined);
    try { await action(); await reload(); setStatus(success); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Operazione workflow non riuscita"); }
    finally { setBusy(false); }
  }

  function correct(event: FormEvent) {
    event.preventDefault();
    run(() => applyAdministrativeCorrection(detail.id, correctionTarget, reason,
      workflow.version, crypto.randomUUID()), "Correzione amministrativa registrata");
  }

  return <section className="workflow-panel" aria-labelledby="workflow-title">
    <div className="section-title"><div><h3 id="workflow-title">Workflow e assegnazione</h3><p>Ogni operazione usa la versione {workflow.version} e viene registrata in modo idempotente.</p></div><button disabled={busy} onClick={() => run(reload, "Workflow aggiornato")}>{text("button.refreshWorkflow")}</button></div>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}
    {status ? <p className="status" role="status">{status}</p> : null}
    <div className="workflow-summary"><span className="state-badge">{workflow.state}</span><span>Versione {workflow.version}</span><span>Prima anteprima: {formatDate(workflow.firstPreviewedAt)}</span></div>
    <div className="workflow-missing"><strong>Precondizioni mancanti</strong>{workflow.missingFields.length === 0
      ? <span className="status">Nessuna: il referto è completo.</span>
      : <ul>{workflow.missingFields.map((field) => <li key={field}>{missingFieldLabel(field)}</li>)}</ul>}</div>
    <div className="workflow-actions">
      <div className="workflow-action-card"><h4>Firmatario</h4><label>Assegnazione<select value={selectedSigner} disabled={busy || !workflow.signerAssignmentAllowed} onChange={(event) => setSelectedSigner(event.target.value)}><option value="">Non assegnato</option>{signers.map((signer) => <option key={signer.id} value={signer.id}>{signer.displayName} · {signer.username}</option>)}</select></label><div className="form-actions"><button className="primary" disabled={busy || !workflow.signerAssignmentAllowed || !selectedSigner} onClick={() => run(() => assignWorkflowSigner(detail.id, selectedSigner, workflow.version, crypto.randomUUID()), "Firmatario assegnato")}>{text("button.assignSigner")}</button><button disabled={busy || !workflow.signerAssignmentAllowed || !workflow.assignedSignerId} onClick={() => run(() => assignWorkflowSigner(detail.id, undefined, workflow.version, crypto.randomUUID()), "Assegnazione rimossa")}>{text("button.clearSigner")}</button></div></div>
      <div className="workflow-action-card"><h4>Completezza</h4><p>Ricalcola lo stato dalle precondizioni correnti: firmatario, metadati e documento attivo.</p><button className="primary" disabled={busy || !workflow.readinessEvaluationAllowed} onClick={() => run(() => evaluateWorkflowReadiness(detail.id, workflow.version, crypto.randomUUID()), "Completezza verificata")}>{text("button.evaluateReadiness")}</button></div>
      <form className="workflow-action-card" onSubmit={correct}><h4>Correzione amministrativa</h4><label>Stato coerente<select value={correctionTarget} disabled={busy || !workflow.administrativeCorrectionAllowed} onChange={(event) => setCorrectionTarget(event.target.value as ReportState)}><option value="INCOMPLETE">INCOMPLETE</option><option value="MISSING_SIGNER">MISSING_SIGNER</option><option value="READY_TO_SIGN">READY_TO_SIGN</option></select></label><label>Motivazione<textarea required maxLength={500} value={reason} disabled={busy || !workflow.administrativeCorrectionAllowed} onChange={(event) => setReason(event.target.value)} /></label><button className="danger" disabled={busy || !workflow.administrativeCorrectionAllowed || !reason.trim()} type="submit">{text("button.adminCorrection")}</button></form>
    </div>
    <h4>Cronologia transizioni</h4>{workflow.history.length === 0
      ? <p className="empty-state">Nessuna operazione registrata.</p>
      : <div className="table-wrap"><table><thead><tr><th>Data</th><th>Operazione</th><th>Transizione</th><th>Attore</th><th>Versione</th><th>Motivazione</th></tr></thead><tbody>{workflow.history.map((item) => <tr key={item.id}><td>{formatDate(item.createdAt)}</td><td>{item.operationType}</td><td>{item.fromState} → {item.toState}</td><td>{item.actorUsername}</td><td>{item.previousVersion} → {item.resultingVersion}</td><td>{item.reason ?? "—"}</td></tr>)}</tbody></table></div>}
  </section>;
}

const missingFieldLabels: Record<string, string> = {
  SIGNER: "Firmatario non assegnato", SIGNER_INACTIVE: "Firmatario non attivo",
  SIGNER_ROLE: "Ruolo firmatario assente", SIGNER_FISCAL_CODE: "Codice fiscale del firmatario assente",
  PRACTICE: "Pratica assente", PATIENT_IDENTIFIER: "Identificativo paziente assente",
  PATIENT_NAME: "Nome paziente incompleto", PATIENT_FISCAL_CODE: "Codice fiscale paziente assente",
  DOCUMENT_TYPE: "Tipo documento assente", DEPARTMENT: "Reparto assente", PRODUCED_AT: "Data produzione assente",
  SOURCE_SYSTEM_INACTIVE: "Sistema erogante non attivo", ACTIVE_DOCUMENT: "Documento clinico attivo assente",
};

function missingFieldLabel(field: string) { return missingFieldLabels[field] ?? field; }
function formatDate(value?: string) { return value ? new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—"; }
