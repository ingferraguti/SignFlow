"use client";

import { FormEvent, useState } from "react";
import {
  configureReportReview, prepareCounterSignature, returnReportReview,
  type ReportDetail, type ReportReview, type WorkflowApprover, type WorkflowSigner,
} from "../lib/adminReports";
import { useUiTexts } from "./UiTextProvider";

export function AdminReportReviewPanel({ detail, review, approvers, signers, reload }: {
  detail: ReportDetail; review: ReportReview; approvers: WorkflowApprover[]; signers: WorkflowSigner[];
  reload: () => Promise<void>;
}) {
  const { text } = useUiTexts();
  const [approverId, setApproverId] = useState(review.assignedApproverId ?? "");
  const [separation, setSeparation] = useState(review.separationRequired);
  const [counterRequired, setCounterRequired] = useState(review.counterSignatureRequired);
  const [counterSignerId, setCounterSignerId] = useState(review.counterSignerId ?? "");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string>(); const [status, setStatus] = useState<string>();
  const participants = [...approvers, ...signers].filter((item, index, all) => all.findIndex((entry) => entry.id === item.id) === index);

  async function run(action: () => Promise<unknown>, message: string) {
    setBusy(true); setError(undefined); setStatus(undefined);
    try { await action(); await reload(); setStatus(message); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Operazione di revisione non riuscita"); }
    finally { setBusy(false); }
  }
  function configure(event: FormEvent) {
    event.preventDefault();
    run(() => configureReportReview(detail.id, { approverId: approverId || undefined, separationRequired: separation,
      counterSignatureRequired: counterRequired, counterSignerId: counterSignerId || undefined,
      expectedVersion: review.version, operationKey: crypto.randomUUID() }), "Regole di revisione aggiornate");
  }
  return <section className="workflow-panel review-admin" aria-labelledby="review-admin-title">
    <div className="section-title"><div><h3 id="review-admin-title">Revisione, approvazione e controfirma</h3><p>Ruoli separati, decisioni append-only e versione {review.version}.</p></div><span className="state-badge">{review.state}</span></div>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}{status ? <p className="status" role="status">{status}</p> : null}
    <div className="workflow-actions">
      <form className="workflow-action-card" onSubmit={configure}><h4>Regole di revisione</h4>
        <label>Approvatore<select value={approverId} disabled={busy} onChange={(event) => setApproverId(event.target.value)}><option value="">Non assegnato</option>{approvers.map((item) => <option key={item.id} value={item.id}>{item.displayName} · {item.username}</option>)}</select></label>
        <label className="inline-check"><input type="checkbox" checked={separation} onChange={(event) => setSeparation(event.target.checked)} />Separazione produttore/firmatario/approvatore</label>
        <label className="inline-check"><input type="checkbox" checked={counterRequired} onChange={(event) => setCounterRequired(event.target.checked)} />Controfirma richiesta</label>
        <label>Controfirmatario<select value={counterSignerId} disabled={busy || !counterRequired} onChange={(event) => setCounterSignerId(event.target.value)}><option value="">Da scegliere</option>{participants.map((item) => <option key={item.id} value={item.id}>{item.displayName} · {item.username}</option>)}</select></label>
        <button className="primary" disabled={busy} type="submit">{text("button.configureReview")}</button>
      </form>
      <div className="workflow-action-card"><h4>Ritorno controllato</h4><p>Consentito soltanto da REVIEW_PENDING o APPROVED e sempre con motivazione.</p><label>Motivazione<textarea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label><button className="danger" disabled={busy || !review.returnAllowed || !reason.trim()} onClick={() => run(() => returnReportReview(detail.id, review.version, reason, crypto.randomUUID()), "Ritorno registrato")}>{text("button.returnReview")}</button></div>
      <div className="workflow-action-card"><h4>Controfirma</h4><p>Nessuna firma digitale viene eseguita: viene predisposto il partecipante successivo.</p><strong>{review.counterSignaturePreparedAt ? `Predisposta per ${review.counterSignerUsername ?? "utente configurato"}` : "Non predisposta"}</strong><button className="primary" disabled={busy || !review.counterSignaturePreparationAllowed || !counterSignerId} onClick={() => run(() => prepareCounterSignature(detail.id, review.version, counterSignerId, crypto.randomUUID()), "Controfirma predisposta")}>{text("button.prepareCounterSignature")}</button></div>
    </div>
    <h4>Timeline delle decisioni</h4>{review.timeline.length === 0 ? <p className="empty-state">Nessuna decisione registrata.</p> : <div className="decision-timeline">{review.timeline.map((item) => <article key={item.id}><span className="state-badge">{item.decisionType}</span><div><strong>{item.actorUsername}</strong><small>{new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "short" }).format(new Date(item.createdAt))} · {item.fromState} → {item.toState}</small>{item.reason ? <p>{item.reason}</p> : null}</div></article>)}</div>}
  </section>;
}
