"use client";

import { useEffect, useState } from "react";
import { fetchSignerProfile, setPreferredDigitalSignature, type SignerProfile } from "../lib/signerPortal";
import { PortalError } from "./SignerHomePanel";
import { useUiTexts } from "./UiTextProvider";

export function SignerProfilePanel() {
  const { text } = useUiTexts();
  const [profile, setProfile] = useState<SignerProfile>(); const [error, setError] = useState<Error>();
  const [saving, setSaving] = useState(false); const [selectedSignature, setSelectedSignature] = useState("");
  const load = () => { setError(undefined); fetchSignerProfile().then(setProfile).catch((reason) => setError(reason instanceof Error ? reason : new Error("Profilo non disponibile"))); };
  useEffect(() => {
    let active = true;
    fetchSignerProfile()
      .then((loadedProfile) => { if (active) { setProfile(loadedProfile); setSelectedSignature(loadedProfile.digitalSignatures.find((item) => item.preferred)?.id ?? ""); } })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason : new Error("Profilo non disponibile")); });
    return () => { active = false; };
  }, []);
  if (error) return <PortalError error={error} retry={load} />;
  if (!profile) return <section className="card signer-wide" aria-busy="true"><h1>Profilo utente</h1><p>Caricamento profilo…</p></section>;
  async function savePreferred() {
    if (!selectedSignature) return;
    setSaving(true); setError(undefined);
    try { setProfile(await setPreferredDigitalSignature(selectedSignature)); }
    catch (reason) { setError(reason instanceof Error ? reason : new Error("Firma preferita non modificata")); }
    finally { setSaving(false); }
  }
  return <div className="signer-stack signer-wide">
    <section className="card"><h1>Profilo utente</h1><dl className="detail-grid">
      <Detail label="Nome" value={`${profile.firstName} ${profile.lastName}`} />
      <Detail label="Account corrente" value={profile.username} /><Detail label="Email" value={profile.email} />
      <Detail label="Persona naturale" value={profile.naturalPersonId} />
      <Detail label="Identificativo personale" value={`${profile.identifierScheme} · ${profile.issuingCountry} · ${profile.maskedPersonalIdentifier}`} />
      <Detail label="Azienda" value={`${profile.companyName} (${profile.companyCode})`} />
      <Detail label="Partizione" value={`${profile.partitionName} (${profile.partitionCode})`} />
      <Detail label="Gruppi autorizzativi" value={profile.groups.join(", ") || "Nessuno"} />
    </dl></section>
    <section className="card"><h2>Account di accesso collegati</h2><div className="table-wrap"><table className="compact-table"><thead><tr><th>Account</th><th>Metodo</th><th>Issuer</th></tr></thead><tbody>{profile.authenticationAccounts.map((account) => <tr key={`${account.issuer}-${account.username}`}><td>{account.username}{account.current ? " · corrente" : ""}</td><td>{account.authenticationMethod}</td><td>{account.issuer}</td></tr>)}</tbody></table></div></section>
    <section className="card"><h2>Firme digitali disponibili</h2>
      {profile.digitalSignatures.length ? <><label>Firma digitale preferita<select value={selectedSignature} disabled={saving} onChange={(event) => setSelectedSignature(event.target.value)}>{profile.digitalSignatures.map((signature) => <option key={signature.id} value={signature.id}>{signature.displayName} · {signature.providerCode} · {signature.signatureType}{signature.qualified ? " · qualificata" : ""}</option>)}</select></label><button className="primary" disabled={saving || !selectedSignature || profile.digitalSignatures.some((item) => item.id === selectedSignature && item.preferred)} onClick={savePreferred}>{text("button.changePreferredDigitalSignature")}</button></> : <p>Nessuna firma digitale attiva disponibile per questa persona.</p>}
    </section>
  </div>;
}
function Detail({ label, value }: { label: string; value?: string }) { return <div><dt>{label}</dt><dd>{value || "—"}</dd></div>; }
