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
  "button.uploadDocument": "Bottone: carica PDF", "button.previewDocument": "Bottone: anteprima PDF",
  "button.downloadDocument": "Bottone: scarica PDF", "button.temporaryUrl": "Bottone: URL temporaneo",
  "button.deleteDocument": "Bottone: elimina documento", "button.closePreview": "Bottone: chiudi anteprima",
  "menu.signerHome": "Menu firmatario: home", "menu.signerReports": "Menu firmatario: referti",
  "menu.signerStates": "Menu firmatario: legenda stati", "menu.signerInfo": "Menu firmatario: informazioni",
  "menu.signerProfile": "Menu firmatario: profilo",
  "button.simpleSearch": "Bottone: ricerca semplice", "button.advancedSearch": "Bottone: ricerca avanzata",
  "button.openReport": "Bottone: apri referto", "button.previewPdf": "Bottone: visualizza PDF",
  "button.downloadPdf": "Bottone: scarica PDF", "button.retry": "Bottone: riprova",
  "button.backToReports": "Bottone: torna ai referti", "button.clearSearch": "Bottone: azzera ricerca",
  "button.assignSigner": "Bottone workflow: assegna firmatario",
  "button.clearSigner": "Bottone workflow: rimuovi assegnazione",
  "button.evaluateReadiness": "Bottone workflow: verifica completezza",
  "button.adminCorrection": "Bottone workflow: correzione amministrativa",
  "button.refreshWorkflow": "Bottone workflow: aggiorna dati",
  "menu.approvals": "Menu approvatore: approvazioni",
  "button.requestApproval": "Bottone revisione: richiedi approvazione",
  "button.approveReport": "Bottone revisione: approva referto",
  "button.rejectReport": "Bottone revisione: rifiuta referto",
  "button.returnReview": "Bottone revisione: ritorna al passaggio precedente",
  "button.configureReview": "Bottone revisione: salva regole",
  "button.prepareCounterSignature": "Bottone revisione: predisponi controfirma",
  "button.openReviewDocument": "Bottone revisione: apri documento",
  "menu.signatureBatches": "Menu firmatario: firma mock",
  "button.startProviderSession": "Bottone firma mock: avvia sessione",
  "button.createManualBatch": "Bottone firma mock: batch manuale",
  "button.signAllFiltered": "Bottone firma mock: firma tutti filtrati",
  "button.confirmBatch": "Bottone firma mock: conferma batch",
  "button.startBatch": "Bottone firma mock: avvia batch",
  "button.cancelBatch": "Bottone firma mock: annulla batch",
  "button.retrySignature": "Bottone firma mock: retry",
  "button.signMockSingle": "Bottone firma mock: firma singola",
  "button.openSignatureBatch": "Bottone firma mock: apri riepilogo",
  "button.downloadMockArtifact": "Bottone firma mock: scarica attestazione",
  "button.searchAudit": "Bottone audit: cerca eventi",
  "button.exportAudit": "Bottone audit: esporta CSV",
  "button.saveRetention": "Bottone audit: salva retention",
  "button.applyRetention": "Bottone audit: applica retention",
  "button.openTimeline": "Bottone audit: apri timeline",
  "label.auditTitle": "Titolo: audit e monitoraggio",
  "label.reportTimeline": "Titolo: timeline della pratica",
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
