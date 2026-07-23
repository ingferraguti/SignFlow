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
