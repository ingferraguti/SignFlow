"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  fetchOrganizationItems, saveOrganizationItem, setOrganizationItemActive,
  type OrganizationItem, type OrganizationItemRequest, type OrganizationType,
} from "../lib/adminUsers";
import { useUiTexts } from "./UiTextProvider";

const labels: Record<OrganizationType, string> = { partitions: "Partizioni", companies: "Aziende", groups: "Gruppi" };
const types = Object.keys(labels) as OrganizationType[];

export function AdminOrganizationPanel() {
  const { text } = useUiTexts();
  const [type, setType] = useState<OrganizationType>("partitions");
  const [items, setItems] = useState<Record<OrganizationType, OrganizationItem[]>>({ partitions: [], companies: [], groups: [] });
  const [editingId, setEditingId] = useState<string>();
  const [form, setForm] = useState<OrganizationItemRequest>({ code: "", name: "", active: true });
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    const [partitions, companies, groups] = await Promise.all(types.map(fetchOrganizationItems));
    setItems({ partitions, companies, groups });
  }, []);

  useEffect(() => { load().catch((reason: Error) => setError(reason.message)); }, [load]);

  function startNew(nextType = type) {
    setType(nextType);
    setEditingId(undefined);
    setForm({ code: "", name: "", active: true, partitionId: nextType === "partitions" ? null : items.partitions[0]?.id });
  }

  function edit(item: OrganizationItem) {
    setEditingId(item.id);
    setForm({ code: item.code, name: item.name, active: item.active, partitionId: item.partitionId });
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(undefined);
    try {
      await saveOrganizationItem(type, form, editingId);
      await load();
      startNew(type);
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Salvataggio non riuscito"); }
  }

  async function toggle(item: OrganizationItem) {
    try { await setOrganizationItemActive(type, item.id, !item.active); await load(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "Aggiornamento non riuscito"); }
  }

  return <section className="card organization-card">
    <div className="section-title"><div><h2>Struttura organizzativa</h2><p>Gestione di partizioni, aziende e gruppi.</p></div>
      <button className="primary" onClick={() => startNew()}>{text("button.newOrganization")}</button></div>
    <div className="admin-tabs">{types.map((itemType) => <button className={type === itemType ? "active" : ""} key={itemType} onClick={() => startNew(itemType)}>{text(`button.${itemType}`)}</button>)}</div>
    {error ? <p className="inline-error" role="alert">{error}</p> : null}
    <div className="organization-grid">
      <div className="table-wrap"><table className="compact-table"><thead><tr><th>Codice</th><th>Nome</th><th>Stato</th><th>Azioni</th></tr></thead><tbody>
        {items[type].map((item) => <tr key={item.id}><td>{item.code}</td><td>{item.name}</td><td>{item.active ? "Attivo" : "Disattivato"}</td><td className="action-cell"><button onClick={() => edit(item)}>{text("button.edit")}</button><button onClick={() => toggle(item)}>{text(item.active ? "button.deactivate" : "button.activate")}</button></td></tr>)}
      </tbody></table></div>
      <form className="admin-form" onSubmit={submit}>
        <h3>{editingId ? `Modifica ${labels[type].toLowerCase()}` : `Nuova voce: ${labels[type]}`}</h3>
        <label>Codice<input required maxLength={60} pattern="[A-Za-z0-9._-]+" value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })} /></label>
        <label>Nome<input required maxLength={160} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
        {type !== "partitions" ? <label>Partizione<select required value={form.partitionId ?? ""} onChange={(event) => setForm({ ...form, partitionId: event.target.value })}><option value="">Seleziona</option>{items.partitions.map((partition) => <option key={partition.id} value={partition.id}>{partition.code} - {partition.name}</option>)}</select></label> : null}
        <label className="checkbox-line"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /> Attivo</label>
        <button className="primary" type="submit">{text("button.confirm")}</button>
      </form>
    </div>
  </section>;
}
