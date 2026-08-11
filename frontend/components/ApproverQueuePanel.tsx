"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchApproverQueue, type ApproverQueueItem } from "../lib/approverPortal";
import { useUiTexts } from "./UiTextProvider";

export function ApproverQueuePanel() {
  const { text } = useUiTexts(); const [items, setItems] = useState<ApproverQueueItem[]>([]); const [error, setError] = useState<string>(); const [loading, setLoading] = useState(true);
  useEffect(() => { fetchApproverQueue().then(setItems).catch((cause: Error) => setError(cause.message)).finally(() => setLoading(false)); }, []);
  return <section className="card signer-wide"><div className="section-title"><div><h1>Revisioni assegnate</h1><p>Documenti in attesa di una decisione indipendente.</p></div><span className="state-badge">{items.length} elementi</span></div>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}{loading ? <p>Caricamento…</p> : null}
    {!loading && items.length === 0 ? <p className="empty-state">Non ci sono revisioni assegnate.</p> : <div className="review-queue">{items.map((item) => <article key={item.id}><div><strong>{item.internalIdentifier}</strong><span>{item.patientName} · {item.documentType} · {item.department}</span></div><span className="state-badge">{item.state}</span><Link className="preview-link" href={`/approvazioni/${item.id}`}>{text("button.openReport")}</Link></article>)}</div>}
  </section>;
}
