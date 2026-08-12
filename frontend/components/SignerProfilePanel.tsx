"use client";

import { useEffect, useState } from "react";
import { fetchSignerProfile, type SignerProfile } from "../lib/signerPortal";
import { PortalError } from "./SignerHomePanel";

export function SignerProfilePanel() {
  const [profile, setProfile] = useState<SignerProfile>(); const [error, setError] = useState<Error>();
  const load = () => { setError(undefined); fetchSignerProfile().then(setProfile).catch((reason) => setError(reason instanceof Error ? reason : new Error("Profilo non disponibile"))); };
  useEffect(() => {
    let active = true;
    fetchSignerProfile()
      .then((loadedProfile) => { if (active) setProfile(loadedProfile); })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason : new Error("Profilo non disponibile")); });
    return () => { active = false; };
  }, []);
  if (error) return <PortalError error={error} retry={load} />;
  if (!profile) return <section className="card signer-wide" aria-busy="true"><h1>Profilo utente</h1><p>Caricamento profilo…</p></section>;
  return <section className="card signer-wide"><h1>Profilo utente</h1><dl className="detail-grid"><Detail label="Nome" value={`${profile.firstName} ${profile.lastName}`} /><Detail label="Username" value={profile.username} /><Detail label="Email" value={profile.email} /><Detail label="Codice fiscale firmatario" value={profile.signerFiscalCode} /><Detail label="Azienda" value={`${profile.companyName} (${profile.companyCode})`} /><Detail label="Partizione" value={`${profile.partitionName} (${profile.partitionCode})`} /><Detail label="Gruppi autorizzativi" value={profile.groups.join(", ") || "Nessuno"} /></dl></section>;
}
function Detail({ label, value }: { label: string; value?: string }) { return <div><dt>{label}</dt><dd>{value || "—"}</dd></div>; }
