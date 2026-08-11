"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { cancelSignatureBatch, confirmSignatureBatch, downloadMockArtifact, fetchSignatureBatch,
  openProviderSession, retrySignatureAttempt, startSignatureBatch, type ProviderSession,
  type SignatureBatch } from "../lib/signaturePortal";
import { formatDate } from "./SignerReportsPanel";
import { useUiTexts } from "./UiTextProvider";

export function SignatureBatchDetailPanel({ batchId }: { batchId: string }) {
  const { text } = useUiTexts(); const [batch, setBatch] = useState<SignatureBatch>();
  const [session, setSession] = useState<ProviderSession>(); const [code, setCode] = useState("");
  const [error, setError] = useState<string>(); const [busy, setBusy] = useState(false);
  useEffect(() => { fetchSignatureBatch(batchId).then(setBatch).catch((reason: Error) => setError(reason.message)); }, [batchId]);
  async function authenticate(event: FormEvent) {
    event.preventDefault(); await action(async () => setSession(await openProviderSession(code)));
  }
  async function action(run: () => Promise<void>) {
    setBusy(true); setError(undefined); try { await run(); } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Operazione non riuscita");
    } finally { setBusy(false); }
  }
  if (!batch) return <section className="card signer-wide">{error ? <p className="inline-error">{error}</p> : <p>Caricamento batch…</p>}</section>;
  const needsSession = batch.state === "DRAFT" || batch.state === "CONFIRMED"
    || batch.attempts.some((attempt) => attempt.state === "FAILED" && attempt.retryCount < attempt.maxRetries);
  return <div className="signer-layout"><section className="card signer-wide"><div className="section-title"><div>
    <h1>Batch {batch.id.slice(0, 8)}</h1><p>Provider {batch.providerCode} · selezione {batch.selectionMode}</p></div>
    <Link className="link-button" href="/firma/batch">Torna ai batch</Link></div>
    <div className="mock-seal">MOCK ONLY · NON È UNA FIRMA DIGITALE VALIDA</div>
    <div className="batch-summary"><div><strong>{batch.totalCount}</strong><span>Documenti</span></div>
      <div><strong>{batch.successCount}</strong><span>Riusciti</span></div>
      <div><strong>{batch.failureCount}</strong><span>Falliti</span></div>
      <div><strong>{batch.state}</strong><span>Stato complessivo</span></div></div>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}
  </section>
  {needsSession ? <section className="card signer-wide"><h2>Sessione temporanea provider</h2>
    <p>La sessione dura 5 minuti. Il codice non viene memorizzato. Per il provider mock demo usa <code>000000</code>.</p>
    <form className="provider-session-form" onSubmit={authenticate}><label>Codice di autorizzazione mock
      <input required value={code} onChange={(event) => setCode(event.target.value)} inputMode="numeric" /></label>
      <button className="primary" disabled={busy}>{text("button.startProviderSession")}</button></form>
    {session ? <p className="status">Sessione attiva fino alle {formatDate(session.expiresAt)}</p> : null}
  </section> : null}
  <section className="card signer-wide"><div className="section-title"><div><h2>Controlli batch</h2>
    <p>Il batch può essere annullato soltanto prima dell’avvio.</p></div></div>
    <div className="batch-actions">
      <button className="primary" disabled={busy || batch.state !== "DRAFT" || !session}
        onClick={() => action(async () => setBatch(await confirmSignatureBatch(batch.id, session!.id)))}>
        {text("button.confirmBatch")}</button>
      <button className="primary" disabled={busy || batch.state !== "CONFIRMED" || !session}
        onClick={() => action(async () => setBatch(await startSignatureBatch(batch.id, session!.id)))}>
        {text("button.startBatch")}</button>
      <button className="danger" disabled={busy || !["DRAFT", "CONFIRMED"].includes(batch.state)}
        onClick={() => action(async () => setBatch(await cancelSignatureBatch(batch.id)))}>
        {text("button.cancelBatch")}</button>
    </div>
  </section>
  <section className="card signer-wide"><h2>Esito per documento</h2><div className="attempt-list">
    {batch.attempts.map((attempt) => <article key={attempt.id}><div className="attempt-heading">
      <div><strong>{attempt.reportIdentifier}</strong><span>Retry: {attempt.retryCount}/{attempt.maxRetries}</span></div>
      <span className={"batch-state " + attempt.state.toLowerCase()}>{attempt.state}</span></div>
      {attempt.errorMessage ? <p className="inline-error"><strong>{attempt.errorCode}</strong> · {attempt.errorMessage}</p> : null}
      {attempt.artifactNotice ? <p className="mock-artifact"><strong>{attempt.artifactNotice}</strong><br />
        Riferimento: {attempt.providerReference}</p> : null}
      <div className="batch-actions">
        {attempt.artifactId ? <button onClick={() => action(() => downloadMockArtifact(attempt.artifactId!, attempt.artifactName!))}>
          {text("button.downloadMockArtifact")}</button> : null}
        {attempt.state === "FAILED" && attempt.retryCount < attempt.maxRetries ? <button className="primary"
          disabled={busy || !session} onClick={() => action(async () => setBatch(await retrySignatureAttempt(batch.id, attempt.id, session!.id)))}>
          {text("button.retrySignature")}</button> : null}
      </div></article>)}
  </div></section>
  <section className="card signer-wide"><h2>Riepilogo finale</h2>
    <dl className="detail-grid"><Detail label="Creato" value={formatDate(batch.createdAt)} />
      <Detail label="Confermato" value={formatDate(batch.confirmedAt)} /><Detail label="Avviato" value={formatDate(batch.startedAt)} />
      <Detail label="Concluso" value={formatDate(batch.completedAt)} /><Detail label="Annullato" value={formatDate(batch.cancelledAt)} /></dl>
  </section></div>;
}
function Detail({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
