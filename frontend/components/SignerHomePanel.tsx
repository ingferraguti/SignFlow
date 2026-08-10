"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { fetchSignerHome, PortalApiError, type SignerHome } from "../lib/signerPortal";
import { useUiTexts } from "./UiTextProvider";

export function SignerHomePanel() {
  const { text } = useUiTexts();
  const [home, setHome] = useState<SignerHome>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error>();
  const load = () => { setLoading(true); setError(undefined); fetchSignerHome().then(setHome).catch(setError).finally(() => setLoading(false)); };
  useEffect(load, []);
  if (loading) return <section className="card signer-wide" aria-busy="true"><h1>Home firmatario</h1><p>Caricamento attività in corso…</p></section>;
  if (error) return <PortalError error={error} retry={load} />;
  return <div className="signer-layout"><section className="card signer-wide"><div className="section-title"><div><h1>Home firmatario</h1><p>La tua area operativa personale</p></div><Link className="primary link-button" href="/firma/referti">{text("button.openReport")}</Link></div>
    <div className="signer-stats"><Stat label="Referti visibili" value={home?.total ?? 0} /><Stat label="Da firmare" value={home?.readyToSign ?? 0} /><Stat label="In revisione" value={home?.reviewPending ?? 0} /><Stat label="Incompleti" value={home?.incomplete ?? 0} /><Stat label="Firmati o inviati" value={home?.signed ?? 0} /></div></section>
    <section className="card signer-wide"><h2>Referti recenti</h2>{home?.recentReports.length ? <div className="recent-list">{home.recentReports.map((report) => <Link key={report.id} href={`/firma/referti/${report.id}`}><strong>{report.patientDisplayName}</strong><span>{report.documentType} · {report.department}</span><span className="state-badge">{report.state}</span></Link>)}</div> : <p className="empty-state">Nessun referto assegnato o autorizzato.</p>}</section></div>;
}

function Stat({ label, value }: { label: string; value: number }) { return <div><strong>{value}</strong><span>{label}</span></div>; }
export function PortalError({ error, retry }: { error: Error; retry?: () => void }) {
  const { text } = useUiTexts(); const expired = error instanceof PortalApiError && error.sessionExpired;
  return <section className="card error signer-wide" role="alert"><h1>{expired ? "Sessione scaduta" : "Servizio non disponibile"}</h1><p>{error.message}</p><div className="action-cell">{expired ? <Link className="primary link-button" href="/login">Accedi</Link> : null}{retry ? <button onClick={retry}>{text("button.retry")}</button> : null}</div></section>;
}
