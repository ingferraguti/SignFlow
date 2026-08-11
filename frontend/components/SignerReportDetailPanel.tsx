"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import type { ClinicalDocument } from "../lib/adminDocuments";
import type { ReportDetail, ReportReview } from "../lib/adminReports";
import { downloadSignerDocument, fetchSignerDocuments, fetchSignerReport, fetchSignerReview, previewSignerDocument, requestSignerReview } from "../lib/signerPortal";
import { PortalError } from "./SignerHomePanel";
import { formatDate } from "./SignerReportsPanel";
import { useUiTexts } from "./UiTextProvider";

export function SignerReportDetailPanel({ reportId }: { reportId: string }) {
  const { text } = useUiTexts(); const [report, setReport] = useState<ReportDetail>();
  const [review, setReview] = useState<ReportReview>();
  const [documents, setDocuments] = useState<ClinicalDocument[]>([]); const [previewUrl, setPreviewUrl] = useState<string>();
  const [loading, setLoading] = useState(true); const [opening, setOpening] = useState(false); const [error, setError] = useState<Error>();
  const load = () => { setLoading(true); setError(undefined); Promise.all([fetchSignerReport(reportId), fetchSignerDocuments(reportId), fetchSignerReview(reportId)])
    .then(([loadedReport, loadedDocuments, loadedReview]) => { setReport(loadedReport); setDocuments(loadedDocuments); setReview(loadedReview); })
    .catch((reason) => setError(reason instanceof Error ? reason : new Error("Referto non disponibile"))).finally(() => setLoading(false)); };
  useEffect(load, [reportId]);
  async function openPdf(document: ClinicalDocument) {
    setOpening(true); setError(undefined);
    try { const preview = await previewSignerDocument(reportId, document.id, report?.workflowVersion ?? 0, crypto.randomUUID()); setPreviewUrl(preview.url); setReport((current) => current ? { ...current, state: preview.reportState, workflowVersion: preview.workflowVersion, firstPreviewedAt: preview.firstPreviewedAt } : current); setReview(await fetchSignerReview(reportId)); }
    catch (reason) { setError(reason instanceof Error ? reason : new Error("Documento non disponibile")); }
    finally { setOpening(false); }
  }
  async function download(document: ClinicalDocument) { try { await downloadSignerDocument(reportId, document); } catch (reason) { setError(reason instanceof Error ? reason : new Error("Documento non disponibile")); } }
  async function requestReview() { if (!review) return; setOpening(true); setError(undefined); try { const result = await requestSignerReview(reportId, review.version); setReview(result.review); setReport((current) => current ? { ...current, state: result.state, workflowVersion: result.version } : current); } catch (reason) { setError(reason instanceof Error ? reason : new Error("Richiesta non registrata")); } finally { setOpening(false); } }
  if (loading) return <section className="card signer-wide" aria-busy="true"><h1>Dettaglio referto</h1><p>Caricamento referto e documento…</p></section>;
  if (error && !report) return <PortalError error={error} retry={load} />;
  if (!report) return null;
  const incomplete = report.state === "INCOMPLETE" || report.state === "MISSING_SIGNER";
  return <div className="signer-layout"><section className="card signer-wide"><div className="section-title"><div><h1>Referto {report.internalIdentifier}</h1><p>Pratica {report.practice.practiceIdentifier}</p></div><Link className="link-button" href="/firma/referti">{text("button.backToReports")}</Link></div>
    <ol className="workflow-steps"><li className={report.firstPreviewedAt ? "done" : "active"}><strong>1</strong><span>Anteprima</span></li><li className={report.state === "REVIEW_PENDING" ? "active" : report.state === "APPROVED" ? "done" : ""}><strong>2</strong><span>Revisione</span></li><li className={report.state === "APPROVED" ? "active" : ""}><strong>3</strong><span>Firma</span><small>Firma digitale non ancora disponibile</small></li></ol>
    {incomplete ? <p className="warning-state" role="status"><strong>Referto incompleto.</strong> Alcune informazioni necessarie alla lavorazione non sono disponibili.</p> : null}
    {error ? <p className="inline-error" role="alert">{error.message}</p> : null}
    <dl className="detail-grid"><Detail label="Paziente" value={`${report.patient.lastName} ${report.patient.firstName}`} /><Detail label="ID paziente" value={report.patient.patientIdentifier} /><Detail label="Tipo referto" value={report.documentType} /><Detail label="Reparto" value={report.department} /><Detail label="Stato" value={report.state} /><Detail label="Sistema erogante" value={report.sourceSystemCode} /><Detail label="Data referto" value={formatDate(report.producedAt)} /><Detail label="Data firma" value={formatDate(report.signedAt)} /><Detail label="ID esterno" value={report.externalIdentifier} /><Detail label="ID FSE" value={report.fseIdentifier} /></dl></section>
    <section className="card signer-wide"><div className="section-title"><div><h2>Documento PDF</h2><p>L’apertura aggiorna lo stato a PREVIEWED quando previsto dal workflow.</p></div></div>
      {documents.length === 0 ? <p className="empty-state"><strong>Documento non disponibile.</strong> Il PDF non è ancora stato acquisito.</p> : documents.map((document) => <div className="signer-document" key={document.id}><div><strong>{document.originalFilename}</strong><small>Versione {document.version} · {formatBytes(document.sizeBytes)}</small></div><div className="action-cell"><button className="primary" disabled={opening} onClick={() => openPdf(document)}>{opening ? "Apertura…" : text("button.previewPdf")}</button><button onClick={() => download(document)}>{text("button.downloadPdf")}</button></div></div>)}
      {previewUrl ? <div className="pdf-preview"><h3>Viewer PDF</h3><iframe title={`PDF ${report.internalIdentifier}`} src={previewUrl} /></div> : null}</section>
    <section className="card signer-wide"><h2>Revisione</h2><p>{review?.approverUsername ? `Approvatore assegnato: ${review.approverUsername}` : "Approvatore non assegnato"}</p><button className="primary" disabled={opening || !review?.requestAllowed} onClick={requestReview}>{text("button.requestApproval")}</button>{report.state === "REVIEW_PENDING" ? <p className="status">Richiesta inviata: decisione in attesa.</p> : null}{report.state === "APPROVED" ? <p className="status">Revisione approvata. Il referto può passare alla firma.</p> : null}</section></div>;
}

function Detail({ label, value }: { label: string; value?: string }) { return <div><dt>{label}</dt><dd>{value || "—"}</dd></div>; }
function formatBytes(size: number) { return size < 1024 ? `${size} B` : `${(size / 1024).toFixed(1)} KB`; }
