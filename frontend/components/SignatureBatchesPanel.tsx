"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchSignatureBatches, type SignatureBatch } from "../lib/signaturePortal";
import { formatDate } from "./SignerReportsPanel";
import { useUiTexts } from "./UiTextProvider";

export function SignatureBatchesPanel() {
  const { text } = useUiTexts(); const [items, setItems] = useState<SignatureBatch[]>([]);
  const [error, setError] = useState<string>(); const [loading, setLoading] = useState(true);
  useEffect(() => { fetchSignatureBatches().then(setItems).catch((reason: Error) => setError(reason.message))
    .finally(() => setLoading(false)); }, []);
  return <div className="signer-layout"><section className="card signer-wide mock-warning">
    <h1>Firma mock</h1><p><strong>Ambiente di collaudo:</strong> nessuna operazione produce una firma digitale valida.
      Gli artefatti scaricabili sono semplici attestazioni testuali marcate MOCK ONLY.</p>
  </section><section className="card signer-wide"><div className="section-title"><div><h2>Batch di firma</h2>
    <p>Seleziona i referti dalla pagina “I miei referti”, quindi conferma e avvia il batch.</p></div>
    <Link className="link-button primary" href="/firma/referti">Seleziona referti</Link></div>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}
    {loading ? <p>Caricamento batch…</p> : items.length === 0 ? <p className="empty-state">Nessun batch creato.</p>
      : <div className="batch-list">{items.map((batch) => <article key={batch.id}><div>
        <strong>Batch {batch.id.slice(0, 8)}</strong><span>{batch.selectionMode} · {formatDate(batch.createdAt)}</span>
        <small>{batch.successCount} riusciti · {batch.failureCount} falliti · {batch.totalCount} totali</small></div>
        <span className={"batch-state " + batch.state.toLowerCase()}>{batch.state}</span>
        <Link className="preview-link" href={"/firma/batch/" + batch.id}>{text("button.openSignatureBatch")}</Link>
      </article>)}</div>}
  </section></div>;
}
