"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { ClinicalDocument } from "../lib/adminDocuments";
import type { ReportDetail, ReportReview } from "../lib/adminReports";
import { approveReport, fetchApproverDocuments, fetchApproverReport, fetchApproverReview, openApproverDocument, rejectReport } from "../lib/approverPortal";
import { useUiTexts } from "./UiTextProvider";

export function ApproverReportPanel({ reportId }: { reportId: string }) {
  const { text } = useUiTexts(); const [report, setReport] = useState<ReportDetail>(); const [review, setReview] = useState<ReportReview>(); const [documents, setDocuments] = useState<ClinicalDocument[]>([]);
  const [preview, setPreview] = useState<string>(); const [reason, setReason] = useState(""); const [busy, setBusy] = useState(false); const [error, setError] = useState<string>(); const [status, setStatus] = useState<string>();
  useEffect(() => {
    let active = true;
    Promise.all([fetchApproverReport(reportId), fetchApproverReview(reportId), fetchApproverDocuments(reportId)])
      .then(([loadedReport, loadedReview, loadedDocuments]) => {
        if (!active) return;
        setReport(loadedReport);
        setReview(loadedReview);
        setDocuments(loadedDocuments);
      })
      .catch((cause: Error) => {
        if (active) setError(cause.message);
      });
    return () => { active = false; };
  }, [reportId]);
  async function run(action: () => Promise<{ review: ReportReview }>, message: string) { setBusy(true); setError(undefined); try { const result = await action(); setReview(result.review); setReport((current) => current ? { ...current, state: result.review.state, workflowVersion: result.review.version } : current); setStatus(message); } catch (cause) { setError(cause instanceof Error ? cause.message : "Decisione non registrata"); } finally { setBusy(false); } }
  async function open(document: ClinicalDocument) { if (!review) return; setBusy(true); setError(undefined); try { const result = await openApproverDocument(reportId, document.id, review.version); setPreview(result.url); setReview(result.review); setReport((current) => current ? { ...current, workflowVersion: result.workflowVersion } : current); setStatus("Presa visione registrata"); } catch (cause) { setError(cause instanceof Error ? cause.message : "Documento non disponibile"); } finally { setBusy(false); } }
  if (!report || !review) return <section className="card signer-wide"><h1>Revisione referto</h1>{error ? <p className="inline-error">{error}</p> : <p>Caricamento…</p>}</section>;
  return <div className="signer-layout"><section className="card signer-wide"><div className="section-title"><div><h1>Revisione {report.internalIdentifier}</h1><p>{report.patient.lastName} {report.patient.firstName} · {report.documentType}</p></div><Link className="link-button" href="/approvazioni">Torna alle revisioni</Link></div>
    <WorkflowSteps state={review.state} />{error ? <p className="inline-error" role="alert">{error}</p> : null}{status ? <p className="status" role="status">{status}</p> : null}
    <dl className="detail-grid"><div><dt>Firmatario</dt><dd>{review.signerUsername ?? "—"}</dd></div><div><dt>Approvatore</dt><dd>{review.approverUsername ?? "—"}</dd></div><div><dt>Separazione ruoli</dt><dd>{review.separationRequired ? "Richiesta" : "Non richiesta"}</dd></div><div><dt>Stato</dt><dd>{review.state}</dd></div></dl></section>
    <section className="card signer-wide"><h2>1. Anteprima e presa visione</h2>{documents.map((document) => <div className="signer-document" key={document.id}><strong>{document.originalFilename}</strong><button className="primary" disabled={busy || review.state !== "REVIEW_PENDING"} onClick={() => open(document)}>{text("button.openReviewDocument")}</button></div>)}{preview ? <div className="pdf-preview"><iframe title="Documento in revisione" src={preview} /></div> : null}</section>
    <section className="card signer-wide"><h2>2. Decisione di revisione</h2><div className="review-decision"><button className="primary" disabled={busy || !review.reviewDecisionAllowed} onClick={() => run(() => approveReport(reportId, review.version), "Referto approvato")}>{text("button.approveReport")}</button><label>Motivazione del rifiuto<textarea required maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label><button className="danger" disabled={busy || !review.reviewDecisionAllowed || !reason.trim()} onClick={() => run(() => rejectReport(reportId, review.version, reason), "Rifiuto registrato")}>{text("button.rejectReport")}</button></div></section>
    <section className="card signer-wide"><h2>Timeline decisioni</h2><div className="decision-timeline">{review.timeline.map((item) => <article key={item.id}><span className="state-badge">{item.decisionType}</span><div><strong>{item.actorUsername}</strong><small>{item.fromState} → {item.toState}</small>{item.reason ? <p>{item.reason}</p> : null}</div></article>)}</div></section></div>;
}

function WorkflowSteps({ state }: { state: string }) { const revisionDone = state === "APPROVED" || state.startsWith("SIGN"); return <ol className="workflow-steps"><li className="done"><strong>1</strong><span>Anteprima</span></li><li className={revisionDone ? "done" : "active"}><strong>2</strong><span>Revisione</span></li><li className={revisionDone ? "active" : ""}><strong>3</strong><span>Firma</span><small>Non ancora eseguita</small></li></ol>; }
