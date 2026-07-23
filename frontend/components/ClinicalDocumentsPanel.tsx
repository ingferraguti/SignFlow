"use client";

import { FormEvent, useEffect, useState } from "react";
import {
  deleteDocument, downloadDocument, fetchDocuments, temporaryDocumentUrl, uploadDocument,
  type ClinicalDocument,
} from "../lib/adminDocuments";
import { useUiTexts } from "./UiTextProvider";

export function ClinicalDocumentsPanel({ reportId }: { reportId: string }) {
  const { text } = useUiTexts();
  const [documents, setDocuments] = useState<ClinicalDocument[]>([]);
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [file, setFile] = useState<File>();
  const [preview, setPreview] = useState<{ url: string; name: string }>();
  const [status, setStatus] = useState("");
  const [error, setError] = useState<string>();

  async function load(showDeleted = includeDeleted) {
    setDocuments(await fetchDocuments(reportId, showDeleted));
  }

  useEffect(() => {
    fetchDocuments(reportId, false).then(setDocuments).catch((reason: Error) => setError(reason.message));
  }, [reportId]);

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(undefined); setStatus("");
    if (!file) { setError("Seleziona un PDF"); return; }
    try {
      const uploaded = await uploadDocument(reportId, file);
      setFile(undefined); setStatus(`Versione ${uploaded.version} caricata e verificata`); await load();
      const input = document.getElementById(`document-upload-${reportId}`) as HTMLInputElement | null;
      if (input) input.value = "";
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Upload non riuscito"); }
  }

  async function openPreview(document: ClinicalDocument) {
    setError(undefined);
    try { const temporary = await temporaryDocumentUrl(reportId, document.id); setPreview({ url: temporary.url, name: document.originalFilename }); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Anteprima non disponibile"); }
  }

  async function copyTemporaryUrl(document: ClinicalDocument) {
    setError(undefined);
    try { const temporary = await temporaryDocumentUrl(reportId, document.id); await navigator.clipboard.writeText(temporary.url); setStatus("URL temporaneo copiato"); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "URL non disponibile"); }
  }

  async function remove(document: ClinicalDocument) {
    if (!window.confirm(`Eliminare logicamente ${document.originalFilename}?`)) return;
    setError(undefined);
    try { await deleteDocument(reportId, document.id); setStatus("Documento eliminato logicamente"); setPreview(undefined); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Eliminazione non riuscita"); }
  }

  return <section className="documents-panel">
    <div className="section-title"><div><h3>Documenti clinici e allegati</h3><p>PDF privati su object storage · limite 10 MB</p></div>{status ? <span className="status">{status}</span> : null}</div>
    <form className="document-upload-form" onSubmit={submit}>
      <label>File PDF<input id={`document-upload-${reportId}`} type="file" accept="application/pdf,.pdf" onChange={(event) => setFile(event.target.files?.[0])} /></label>
      <button className="primary" type="submit">{text("button.uploadDocument")}</button>
      <label className="inline-check"><input type="checkbox" checked={includeDeleted} onChange={(event) => { setIncludeDeleted(event.target.checked); load(event.target.checked).catch((reason: Error) => setError(reason.message)); }} />Mostra eliminati</label>
    </form>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}
    <div className="table-wrap"><table className="documents-table"><thead><tr><th>Versione</th><th>File</th><th>Dimensione</th><th>SHA-256</th><th>Upload</th><th>Stato</th><th>Azioni</th></tr></thead><tbody>
      {documents.map((document) => <tr key={document.id}><td>v{document.version}</td><td>{document.originalFilename}<small>{document.mimeType}</small></td><td>{formatSize(document.sizeBytes)}</td><td><code title={document.sha256}>{document.sha256.slice(0, 12)}…</code></td><td>{document.uploadedBy}<small>{formatDate(document.uploadedAt)}</small></td><td><span className="state-badge">{document.status}</span></td><td><div className="document-actions">
        <button disabled={document.status !== "ACTIVE"} onClick={() => openPreview(document)}>{text("button.previewDocument")}</button>
        <button disabled={document.status !== "ACTIVE"} onClick={() => downloadDocument(reportId, document).catch((reason: Error) => setError(reason.message))}>{text("button.downloadDocument")}</button>
        <button disabled={document.status !== "ACTIVE"} onClick={() => copyTemporaryUrl(document)}>{text("button.temporaryUrl")}</button>
        <button className="danger" disabled={document.status !== "ACTIVE"} onClick={() => remove(document)}>{text("button.deleteDocument")}</button>
      </div></td></tr>)}
      {!documents.length ? <tr><td colSpan={7}>Nessun documento associato.</td></tr> : null}
    </tbody></table></div>
    {preview ? <div className="pdf-preview"><div className="section-title"><h4>Anteprima · {preview.name}</h4><button onClick={() => setPreview(undefined)}>{text("button.closePreview")}</button></div><iframe title={`Anteprima ${preview.name}`} src={preview.url} /></div> : null}
  </section>;
}

function formatSize(bytes: number) { return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`; }
function formatDate(value: string) { return new Intl.DateTimeFormat("it-IT", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)); }
