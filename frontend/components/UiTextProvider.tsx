"use client";

import { createContext, useContext, useEffect, useState } from "react";
import { useSession } from "next-auth/react";
import { fetchUiTexts, type UiTexts } from "../lib/adminUsers";

const defaults: UiTexts = {
  "menu.home": "Home", "menu.system": "Stato sistema", "menu.reports": "Referti",
  "menu.signature": "Firma", "menu.monitoring": "Monitoraggio", "menu.configuration": "Configurazione",
  "button.newUser": "Nuovo utente", "button.search": "Cerca", "button.edit": "Modifica",
  "button.activate": "Attiva", "button.deactivate": "Disattiva", "button.confirm": "Conferma",
  "button.newOrganization": "Nuovo", "button.saveTexts": "Salva testi",
  "button.login": "Login with Keycloak", "button.logout": "Logout",
  "button.partitions": "Partizioni", "button.companies": "Aziende", "button.groups": "Gruppi",
  "button.delete": "Elimina", "button.sourceSystems": "Sistemi eroganti",
  "button.signatureProviders": "Provider di firma", "button.signatureAccounts": "Account di firma",
  "button.fseFacilityMappings": "Mappature FSE", "button.newTechnicalConfiguration": "Nuova configurazione",
  "button.viewDetails": "Dettaglio", "button.resetFilters": "Azzera filtri",
  "button.closeDetails": "Chiudi dettaglio", "button.previousPage": "Pagina precedente",
  "button.nextPage": "Pagina successiva",
  "button.uploadDocument": "Carica PDF", "button.previewDocument": "Anteprima",
  "button.downloadDocument": "Scarica", "button.temporaryUrl": "URL temporaneo",
  "button.deleteDocument": "Elimina documento", "button.closePreview": "Chiudi anteprima",
  "menu.signerHome": "Home firmatario", "menu.signerReports": "I miei referti",
  "menu.signerStates": "Legenda stati", "menu.signerInfo": "Informazioni", "menu.signerProfile": "Profilo utente",
  "button.simpleSearch": "Ricerca semplice", "button.advancedSearch": "Ricerca avanzata",
  "button.openReport": "Apri referto", "button.previewPdf": "Visualizza PDF", "button.downloadPdf": "Scarica PDF",
  "button.retry": "Riprova", "button.backToReports": "Torna ai referti", "button.clearSearch": "Azzera ricerca",
  "button.assignSigner": "Assegna firmatario", "button.clearSigner": "Rimuovi assegnazione",
  "button.evaluateReadiness": "Verifica completezza", "button.adminCorrection": "Applica correzione",
  "button.refreshWorkflow": "Aggiorna workflow",
  "menu.approvals": "Approvazioni", "button.requestApproval": "Richiedi approvazione",
  "button.approveReport": "Approva referto", "button.rejectReport": "Rifiuta referto",
  "button.returnReview": "Ritorna al passaggio precedente", "button.configureReview": "Salva regole di revisione",
  "button.prepareCounterSignature": "Predisponi controfirma", "button.openReviewDocument": "Apri documento da revisionare",
};

type UiTextContextValue = { texts: UiTexts; setTexts: (texts: UiTexts) => void; text: (key: string) => string };
const UiTextContext = createContext<UiTextContextValue>({ texts: defaults, setTexts: () => undefined, text: (key) => defaults[key] ?? key });

export function UiTextProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const { status } = useSession();
  const [texts, setTexts] = useState(defaults);
  useEffect(() => {
    if (status === "authenticated") fetchUiTexts().then((loaded) => setTexts({ ...defaults, ...loaded })).catch(() => undefined);
  }, [status]);
  return <UiTextContext.Provider value={{ texts, setTexts, text: (key) => texts[key] ?? defaults[key] ?? key }}>{children}</UiTextContext.Provider>;
}

export function useUiTexts() {
  return useContext(UiTextContext);
}
