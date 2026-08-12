"use client";

import { useEffect, useState } from "react";
import { fetchStateLegend, type StateLegend } from "../lib/signerPortal";
import { PortalError } from "./SignerHomePanel";

export function SignerStateLegendPanel() {
  const [states, setStates] = useState<StateLegend[]>(); const [error, setError] = useState<Error>();
  const load = () => { setError(undefined); fetchStateLegend().then(setStates).catch((reason) => setError(reason instanceof Error ? reason : new Error("Legenda non disponibile"))); };
  useEffect(() => {
    let active = true;
    fetchStateLegend()
      .then((loadedStates) => { if (active) setStates(loadedStates); })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason : new Error("Legenda non disponibile")); });
    return () => { active = false; };
  }, []);
  if (error) return <PortalError error={error} retry={load} />;
  return <section className="card signer-wide"><h1>Legenda stati</h1><p>Significato degli stati applicativi dei referti.</p>{states ? <div className="legend-grid">{states.map((item) => <article key={item.code}><span className="state-badge">{item.code}</span><h2>{item.label}</h2><p>{item.description}</p></article>)}</div> : <p aria-busy="true">Caricamento legenda…</p>}</section>;
}
