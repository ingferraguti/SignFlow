"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  deleteTechnicalConfiguration, fetchTechnicalConfiguration, saveFseFacilityMapping, saveSignatureAccount,
  saveSignatureProvider, saveSourceSystem, saveSourceSystemFseDocumentTypes,
  type FseFacilityMapping, type FseFacilityMappingRequest,
  type SignatureAccount, type SignatureAccountRequest, type SignatureAuthenticationMode,
  type SignatureProvider, type SignatureProviderRequest, type SourceSystem, type SourceSystemRequest,
  type TechnicalConfigurationData,
} from "../lib/adminTechnicalConfig";
import { useUiTexts } from "./UiTextProvider";

type Tab = "sourceSystems" | "fseDocumentTypes" | "signatureProviders" | "signatureAccounts" | "fseFacilityMappings";
const tabs: Tab[] = ["sourceSystems", "fseDocumentTypes", "signatureProviders", "signatureAccounts", "fseFacilityMappings"];
const tabLabels: Record<Tab, string> = {
  sourceSystems: "Sistemi eroganti", fseDocumentTypes: "Tipi documento FSE",
  signatureProviders: "Provider di firma", signatureAccounts: "Account di firma",
  fseFacilityMappings: "Mapping presidi FSE",
};

export function AdminTechnicalConfigurationPanel() {
  const [tab, setTab] = useState<Tab>("sourceSystems");
  const [data, setData] = useState<TechnicalConfigurationData>();
  const [error, setError] = useState<string>();
  const load = useCallback(async () => { setData(await fetchTechnicalConfiguration()); }, []);
  useEffect(() => {
    let active = true;
    fetchTechnicalConfiguration()
      .then((configuration) => {
        if (active) setData(configuration);
      })
      .catch((reason: Error) => {
        if (active) setError(reason.message);
      });
    return () => { active = false; };
  }, []);

  async function execute(action: () => Promise<unknown>) {
    setError(undefined);
    try { await action(); await load(); return true; }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Operazione non riuscita"); return false; }
  }

  if (!data) return <section className="card organization-card"><h2>Configurazioni tecniche</h2>{error ? <p className="inline-error">{error}</p> : <p>Caricamento...</p>}</section>;

  return <section className="card organization-card">
    <div className="section-title"><div><h2>Configurazioni tecniche</h2><p>Pipeline documentali, firma remota e normalizzazione FSE.</p></div></div>
    <div className="admin-tabs technical-tabs">{tabs.map((item) => <button className={tab === item ? "active" : ""} key={item} onClick={() => setTab(item)}>{tabLabels[item]}</button>)}</div>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}
    {tab === "sourceSystems" ? <SourceSystemsSection data={data} execute={execute} /> : null}
    {tab === "fseDocumentTypes" ? <FseDocumentTypesSection data={data} execute={execute} /> : null}
    {tab === "signatureProviders" ? <SignatureProvidersSection data={data} execute={execute} /> : null}
    {tab === "signatureAccounts" ? <SignatureAccountsSection data={data} execute={execute} /> : null}
    {tab === "fseFacilityMappings" ? <FseMappingsSection data={data} execute={execute} /> : null}
  </section>;
}

type SectionProps = { data: TechnicalConfigurationData; execute: (action: () => Promise<unknown>) => Promise<boolean> };

function FseDocumentTypesSection({ data, execute }: SectionProps) {
  const firstSourceId = data.sourceSystems[0]?.id ?? "";
  const [sourceSystemId, setSourceSystemId] = useState(firstSourceId);
  const configured = data.sourceSystemFseDocumentTypes
    .filter((item) => item.sourceSystemId === sourceSystemId && item.cdaInjectionEnabled)
    .map((item) => item.documentTypeCode);
  const [selected, setSelected] = useState<string[]>(configured);
  const source = data.sourceSystems.find((item) => item.id === sourceSystemId);

  function changeSource(id: string) {
    setSourceSystemId(id);
    setSelected(data.sourceSystemFseDocumentTypes
      .filter((item) => item.sourceSystemId === id && item.cdaInjectionEnabled)
      .map((item) => item.documentTypeCode));
  }
  function toggle(code: string, enabled: boolean) {
    setSelected(enabled ? [...selected, code] : selected.filter((item) => item !== code));
  }

  return <div className="technical-grid">
    <div>
      <h3>Catalogo nazionale FSE 2.0</h3>
      <p className="form-hint">Ogni documento usa una natura codificata. Il catalogo è controllato e non accetta valori liberi.</p>
      <div className="table-wrap"><table className="compact-table"><thead><tr><th>Codice</th><th>Tipologia</th><th>Descrizione</th></tr></thead><tbody>
        {data.fseDocumentTypes.map((item) => <tr key={item.code}><td><strong>{item.code}</strong></td><td>{item.displayName}</td><td>{item.description}</td></tr>)}
      </tbody></table></div>
    </div>
    <form className="admin-form" onSubmit={(event) => { event.preventDefault(); execute(() => saveSourceSystemFseDocumentTypes(sourceSystemId, selected)); }}>
      <h3>Attivazione CDA per tipologia</h3>
      <label>Sistema erogante<select value={sourceSystemId} onChange={(event) => changeSource(event.target.value)}>{data.sourceSystems.map((item) => <option key={item.id} value={item.id}>{item.code} - {item.description}</option>)}</select></label>
      <p className="form-hint">La preparazione PDF+CDA si attiva soltanto per le tipologie selezionate. Il sistema deve avere “Creazione CDA” attiva e “Passthrough” disattivo.</p>
      {data.fseDocumentTypes.map((item) => <Check key={item.code} label={`${item.code} · ${item.displayName}`} value={selected.includes(item.code)} onChange={(value) => toggle(item.code, value)} />)}
      {!source?.createCda || source?.passthrough ? <p className="inline-error" role="alert">Abilita prima Creazione CDA e disattiva Passthrough sul sistema erogante.</p> : null}
      <button className="primary" disabled={!sourceSystemId || !source?.createCda || source?.passthrough} type="submit">Salva tipologie abilitate</button>
    </form>
  </div>;
}

function SourceSystemsSection({ data, execute }: SectionProps) {
  const { text } = useUiTexts();
  const blank = (): SourceSystemRequest => ({ code: "", companyId: data.organization.companies[0]?.id ?? "", description: "", active: true, cdaType: "CDA2-REF", pdfA3Conversion: false, visibleSignature: false, multipleSignature: false, sendUnsigned: false, createCda: true, passthrough: false });
  const [id, setId] = useState<string>(); const [form, setForm] = useState<SourceSystemRequest>(blank);
  function edit(item: SourceSystem) { setId(item.id); setForm({ code: item.code, companyId: item.companyId, description: item.description, active: item.active, cdaType: item.cdaType, pdfA3Conversion: item.pdfA3Conversion, visibleSignature: item.visibleSignature, multipleSignature: item.multipleSignature, sendUnsigned: item.sendUnsigned, createCda: item.createCda, passthrough: item.passthrough }); }
  async function submit(event: FormEvent) { event.preventDefault(); if (await execute(() => saveSourceSystem(form, id))) { setId(undefined); setForm(blank()); } }
  const incoherent = form.createCda && form.passthrough;
  return <div className="technical-grid"><ConfigTable headers={["Codice", "Azienda", "CDA", "Pipeline", "Stato"]} rows={data.sourceSystems.map((item) => ({ id: item.id, cells: [item.code, item.companyCode, item.cdaType, `${item.createCda ? "crea CDA" : ""}${item.passthrough ? "passthrough" : ""}`, item.active ? "Attivo" : "Disattivato"], edit: () => edit(item), remove: () => execute(() => deleteTechnicalConfiguration("source-systems", item.id)) }))} />
    <form className="admin-form" onSubmit={submit}><FormTitle id={id} onNew={() => { setId(undefined); setForm(blank()); }} />
      <label>Codice<input required pattern="[A-Za-z0-9._-]+" maxLength={60} value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></label>
      <label>Azienda<select required value={form.companyId} onChange={(e) => setForm({ ...form, companyId: e.target.value })}>{data.organization.companies.map((item) => <option key={item.id} value={item.id}>{item.code} - {item.name}</option>)}</select></label>
      <label>Descrizione<textarea required maxLength={300} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
      <label>Tipo CDA<input required maxLength={60} value={form.cdaType} onChange={(e) => setForm({ ...form, cdaType: e.target.value })} /></label>
      <Check label="Conversione PDF/A3" value={form.pdfA3Conversion} onChange={(value) => setForm({ ...form, pdfA3Conversion: value })} />
      <Check label="Firma visibile" value={form.visibleSignature} onChange={(value) => setForm({ ...form, visibleSignature: value })} />
      <Check label="Firma multipla" value={form.multipleSignature} onChange={(value) => setForm({ ...form, multipleSignature: value })} />
      <Check label="Invio non firmato" value={form.sendUnsigned} onChange={(value) => setForm({ ...form, sendUnsigned: value })} />
      <Check label="Creazione CDA" value={form.createCda} onChange={(value) => setForm({ ...form, createCda: value })} />
      <Check label="Passthrough" value={form.passthrough} onChange={(value) => setForm({ ...form, passthrough: value })} />
      <Check label="Attivo" value={form.active} onChange={(value) => setForm({ ...form, active: value })} />
      {incoherent ? <p className="inline-error" role="alert">Creazione CDA e passthrough non possono essere attivi contemporaneamente.</p> : null}
      <button className="primary" disabled={incoherent} type="submit">{text("button.confirm")}</button>
    </form></div>;
}

function SignatureProvidersSection({ data, execute }: SectionProps) {
  const { text } = useUiTexts();
  const blank = (): SignatureProviderRequest => ({ code: "", name: "", adapterType: "REST", baseUrl: "", authenticationMode: "OTP", credentialReference: "", supportsVisibleSignature: false, supportsMultipleSignature: false, active: true });
  const [id, setId] = useState<string>(); const [form, setForm] = useState<SignatureProviderRequest>(blank);
  function edit(item: SignatureProvider) { setId(item.id); setForm({ code: item.code, name: item.name, adapterType: item.adapterType, baseUrl: item.baseUrl, authenticationMode: item.authenticationMode, credentialReference: item.credentialReference, supportsVisibleSignature: item.supportsVisibleSignature, supportsMultipleSignature: item.supportsMultipleSignature, active: item.active }); }
  async function submit(event: FormEvent) { event.preventDefault(); if (await execute(() => saveSignatureProvider(form, id))) { setId(undefined); setForm(blank()); } }
  const modes: SignatureAuthenticationMode[] = ["NONE", "OTP", "USERNAME_OTP", "OAUTH2", "CERTIFICATE", "API_KEY_REFERENCE"];
  return <div className="technical-grid"><ConfigTable headers={["Codice", "Nome", "Adapter", "Autenticazione", "Stato"]} rows={data.signatureProviders.map((item) => ({ id: item.id, cells: [item.code, item.name, item.adapterType, item.authenticationMode, item.active ? "Attivo" : "Disattivato"], edit: () => edit(item), remove: () => execute(() => deleteTechnicalConfiguration("signature-providers", item.id)) }))} />
    <form className="admin-form" onSubmit={submit}><FormTitle id={id} onNew={() => { setId(undefined); setForm(blank()); }} />
      <label>Codice<input required pattern="[A-Za-z0-9._-]+" maxLength={60} value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></label>
      <label>Nome<input required maxLength={160} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
      <label>Tipo adapter<input required maxLength={80} value={form.adapterType} onChange={(e) => setForm({ ...form, adapterType: e.target.value })} /></label>
      <label>Base URL<input type="url" maxLength={500} value={form.baseUrl} onChange={(e) => setForm({ ...form, baseUrl: e.target.value })} /></label>
      <label>Modalità autenticazione<select value={form.authenticationMode} onChange={(e) => setForm({ ...form, authenticationMode: e.target.value as SignatureAuthenticationMode })}>{modes.map((mode) => <option key={mode}>{mode}</option>)}</select></label>
      <label>Riferimento credenziale esterna<input maxLength={200} placeholder="secret://... (nessuna password)" value={form.credentialReference} onChange={(e) => setForm({ ...form, credentialReference: e.target.value })} /></label>
      <Check label="Supporta firma visibile" value={form.supportsVisibleSignature} onChange={(value) => setForm({ ...form, supportsVisibleSignature: value })} />
      <Check label="Supporta firma multipla" value={form.supportsMultipleSignature} onChange={(value) => setForm({ ...form, supportsMultipleSignature: value })} />
      <Check label="Attivo" value={form.active} onChange={(value) => setForm({ ...form, active: value })} />
      <button className="primary" type="submit">{text("button.confirm")}</button>
    </form></div>;
}

function SignatureAccountsSection({ data, execute }: SectionProps) {
  const { text } = useUiTexts();
  const signers = Array.from(new Map(data.users
    .filter((user) => user.roles.some((role) => role.code === "SIGNER"))
    .map((user) => [user.naturalPersonId, user])).values());
  const blank = (): SignatureAccountRequest => ({ applicationUserId: signers[0]?.id ?? "", signatureProviderId: data.signatureProviders[0]?.id ?? "", accountAlias: "", providerUsername: "", certificateAlias: "", displayName: "", signatureType: "REMOTE", qualified: false, active: true });
  const [id, setId] = useState<string>(); const [form, setForm] = useState<SignatureAccountRequest>(blank);
  function edit(item: SignatureAccount) { setId(item.id); setForm({ applicationUserId: item.applicationUserId, signatureProviderId: item.signatureProviderId, accountAlias: item.accountAlias, providerUsername: item.providerUsername, certificateAlias: item.certificateAlias, displayName: item.displayName, signatureType: item.signatureType, qualified: item.qualified, active: item.active }); }
  async function submit(event: FormEvent) { event.preventDefault(); if (await execute(() => saveSignatureAccount(form, id))) { setId(undefined); setForm(blank()); } }
  return <div className="technical-grid"><ConfigTable headers={["Firma digitale", "Persona naturale", "Provider", "Certificato", "Stato"]} rows={data.signatureAccounts.map((item) => ({ id: item.id, cells: [item.displayName, item.naturalPersonId, item.signatureProviderCode, item.certificateAlias, item.active ? "Attiva" : "Disattivata"], edit: () => edit(item), remove: () => execute(() => deleteTechnicalConfiguration("signature-accounts", item.id)) }))} />
    <form className="admin-form" onSubmit={submit}><FormTitle id={id} onNew={() => { setId(undefined); setForm(blank()); }} />
      <label>Persona naturale<select required value={form.applicationUserId} onChange={(e) => setForm({ ...form, applicationUserId: e.target.value })}>{signers.map((user) => <option key={user.id} value={user.id}>{user.lastName} {user.firstName} · {user.personalIdentifier} · account {user.username}</option>)}</select></label>
      <label>Provider<select required value={form.signatureProviderId} onChange={(e) => setForm({ ...form, signatureProviderId: e.target.value })}>{data.signatureProviders.map((provider) => <option key={provider.id} value={provider.id}>{provider.code} - {provider.name}</option>)}</select></label>
      <label>Alias account<input required maxLength={120} value={form.accountAlias} onChange={(e) => setForm({ ...form, accountAlias: e.target.value })} /></label>
      <label>Username provider<input maxLength={160} value={form.providerUsername} onChange={(e) => setForm({ ...form, providerUsername: e.target.value })} /></label>
      <label>Alias certificato<input maxLength={200} value={form.certificateAlias} onChange={(e) => setForm({ ...form, certificateAlias: e.target.value })} /></label>
      <label>Nome firma digitale<input required maxLength={160} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} /></label>
      <label>Tipo firma<select value={form.signatureType} onChange={(e) => setForm({ ...form, signatureType: e.target.value })}><option>REMOTE</option><option>LOCAL_TEST</option><option>MOCK</option></select></label>
      <Check label="Firma qualificata" value={form.qualified} onChange={(qualified) => setForm({ ...form, qualified })} />
      <p className="form-hint">Le password del provider non vengono richieste né memorizzate.</p>
      <Check label="Attivo" value={form.active} onChange={(value) => setForm({ ...form, active: value })} />
      <button className="primary" type="submit">{text("button.confirm")}</button>
    </form></div>;
}

function FseMappingsSection({ data, execute }: SectionProps) {
  const { text } = useUiTexts();
  const firstCompany = data.organization.companies[0]?.id ?? "";
  const blank = (): FseFacilityMappingRequest => ({ facilityCode: "", facilityName: "", companyId: firstCompany, operatingUnit: "", department: "", sourceSystemId: data.sourceSystems.find((item) => item.companyId === firstCompany)?.id ?? "", active: true });
  const [id, setId] = useState<string>(); const [form, setForm] = useState<FseFacilityMappingRequest>(blank);
  const compatibleSources = data.sourceSystems.filter((item) => item.companyId === form.companyId);
  function edit(item: FseFacilityMapping) { setId(item.id); setForm({ facilityCode: item.facilityCode, facilityName: item.facilityName, companyId: item.companyId, operatingUnit: item.operatingUnit, department: item.department, sourceSystemId: item.sourceSystemId, active: item.active }); }
  async function submit(event: FormEvent) { event.preventDefault(); if (await execute(() => saveFseFacilityMapping(form, id))) { setId(undefined); setForm(blank()); } }
  return <div className="technical-grid"><ConfigTable headers={["Presidio", "Azienda", "Unità operativa", "Reparto", "Sistema", "Stato"]} rows={data.fseFacilityMappings.map((item) => ({ id: item.id, cells: [item.facilityCode, item.companyCode, item.operatingUnit, item.department, item.sourceSystemCode, item.active ? "Attivo" : "Disattivato"], edit: () => edit(item), remove: () => execute(() => deleteTechnicalConfiguration("fse-facility-mappings", item.id)) }))} />
    <form className="admin-form" onSubmit={submit}><FormTitle id={id} onNew={() => { setId(undefined); setForm(blank()); }} />
      <label>Codice presidio<input required pattern="[A-Za-z0-9._-]+" maxLength={80} value={form.facilityCode} onChange={(e) => setForm({ ...form, facilityCode: e.target.value })} /></label>
      <label>Nome presidio<input required maxLength={200} value={form.facilityName} onChange={(e) => setForm({ ...form, facilityName: e.target.value })} /></label>
      <label>Azienda<select required value={form.companyId} onChange={(e) => { const companyId = e.target.value; setForm({ ...form, companyId, sourceSystemId: data.sourceSystems.find((item) => item.companyId === companyId)?.id ?? "" }); }}>{data.organization.companies.map((item) => <option key={item.id} value={item.id}>{item.code} - {item.name}</option>)}</select></label>
      <label>Unità operativa<input required maxLength={160} value={form.operatingUnit} onChange={(e) => setForm({ ...form, operatingUnit: e.target.value })} /></label>
      <label>Reparto<input required maxLength={160} value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} /></label>
      <label>Sistema erogante<select required value={form.sourceSystemId} onChange={(e) => setForm({ ...form, sourceSystemId: e.target.value })}><option value="">Seleziona</option>{compatibleSources.map((item) => <option key={item.id} value={item.id}>{item.code}</option>)}</select></label>
      <Check label="Attivo" value={form.active} onChange={(value) => setForm({ ...form, active: value })} />
      <button className="primary" type="submit">{text("button.confirm")}</button>
    </form></div>;
}

type Row = { id: string; cells: string[]; edit: () => void; remove: () => void };
function ConfigTable({ headers, rows }: { headers: string[]; rows: Row[] }) {
  const { text } = useUiTexts();
  return <div className="table-wrap"><table className="compact-table"><thead><tr>{headers.map((header) => <th key={header}>{header}</th>)}<th>Azioni</th></tr></thead><tbody>{rows.map((row) => <tr key={row.id}>{row.cells.map((cell, index) => <td key={`${row.id}-${index}`}>{cell || "—"}</td>)}<td className="action-cell"><button onClick={row.edit}>{text("button.edit")}</button><button className="danger" onClick={() => { if (window.confirm("Eliminare definitivamente questa configurazione?")) row.remove(); }}>{text("button.delete")}</button></td></tr>)}</tbody></table></div>;
}

function FormTitle({ id, onNew }: { id?: string; onNew: () => void }) {
  const { text } = useUiTexts();
  return <div className="section-title"><h3>{id ? "Modifica configurazione" : "Nuova configurazione"}</h3>{id ? <button type="button" onClick={onNew}>{text("button.newTechnicalConfiguration")}</button> : null}</div>;
}

function Check({ label, value, onChange }: { label: string; value: boolean; onChange: (value: boolean) => void }) {
  return <label className="checkbox-line"><input type="checkbox" checked={value} onChange={(event) => onChange(event.target.checked)} /> {label}</label>;
}
