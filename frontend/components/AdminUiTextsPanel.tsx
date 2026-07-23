"use client";

import { FormEvent, useEffect, useState } from "react";
import { fetchUiTexts, saveUiTexts, type UiTexts } from "../lib/adminUsers";
import { useUiTexts } from "./UiTextProvider";

const textLabels: Record<string, string> = {
  "menu.home": "Menu: Home", "menu.system": "Menu: stato sistema", "menu.reports": "Menu: referti",
  "menu.signature": "Menu: firma", "menu.monitoring": "Menu: monitoraggio", "menu.configuration": "Menu: configurazione",
  "button.newUser": "Bottone: nuovo utente", "button.search": "Bottone: cerca", "button.edit": "Bottone: modifica",
  "button.activate": "Bottone: attiva", "button.deactivate": "Bottone: disattiva", "button.confirm": "Bottone: conferma",
  "button.newOrganization": "Bottone: nuova voce organizzativa", "button.saveTexts": "Bottone: salva testi",
  "button.login": "Bottone: login", "button.logout": "Bottone: logout",
  "button.partitions": "Bottone: partizioni", "button.companies": "Bottone: aziende", "button.groups": "Bottone: gruppi",
  "button.delete": "Bottone: elimina", "button.sourceSystems": "Bottone: sistemi eroganti",
  "button.signatureProviders": "Bottone: provider di firma", "button.signatureAccounts": "Bottone: account di firma",
  "button.fseFacilityMappings": "Bottone: mappature FSE", "button.newTechnicalConfiguration": "Bottone: nuova configurazione tecnica",
  "button.viewDetails": "Bottone: dettaglio referto", "button.resetFilters": "Bottone: azzera filtri",
  "button.closeDetails": "Bottone: chiudi dettaglio", "button.previousPage": "Bottone: pagina precedente",
  "button.nextPage": "Bottone: pagina successiva",
};

export function AdminUiTextsPanel() {
  const context = useUiTexts();
  const [form, setForm] = useState<UiTexts>({});
  const [status, setStatus] = useState("");
  const [error, setError] = useState<string>();
  useEffect(() => { fetchUiTexts().then(setForm).catch((reason: Error) => setError(reason.message)); }, []);

  async function submit(event: FormEvent) {
    event.preventDefault(); setError(undefined);
    try { const saved = await saveUiTexts(form); setForm(saved); context.setTexts(saved); setStatus("Testi aggiornati"); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Salvataggio non riuscito"); }
  }

  return <section className="card organization-card"><div className="section-title"><div><h2>Profilo admin · Testi e traduzioni</h2><p>Personalizza le voci di menu e i testi dei bottoni.</p></div>{status ? <span className="status">{status}</span> : null}</div>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}
    <form className="translation-form" onSubmit={submit}>{Object.entries(textLabels).map(([key, label]) => <label key={key}>{label}<input required maxLength={300} value={form[key] ?? ""} onChange={(event) => setForm({ ...form, [key]: event.target.value })} /></label>)}
      <button className="primary" type="submit">{context.text("button.saveTexts")}</button></form>
  </section>;
}
