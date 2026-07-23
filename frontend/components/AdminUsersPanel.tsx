"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  fetchOrganizationOptions,
  fetchUsers,
  saveUser,
  setUserActive,
  type ApplicationUser,
  type ApplicationUserRequest,
  type Option,
  type OrganizationOptions,
} from "../lib/adminUsers";

const emptyOptions: OrganizationOptions = { partitions: [], companies: [], roles: [], groups: [] };

function emptyUser(options: OrganizationOptions): ApplicationUserRequest {
  return {
    username: "",
    oidcSubject: "",
    firstName: "",
    lastName: "",
    email: "",
    fiscalCode: "",
    signerFiscalCode: "",
    counterSignerFiscalCode: "",
    active: true,
    partitionId: options.partitions[0]?.id ?? "",
    companyId: options.companies[0]?.id ?? "",
    roleIds: [],
    groupIds: [],
  };
}

function fromUser(user: ApplicationUser): ApplicationUserRequest {
  return {
    username: user.username,
    oidcSubject: user.oidcSubject,
    firstName: user.firstName,
    lastName: user.lastName,
    email: user.email ?? "",
    fiscalCode: user.fiscalCode ?? "",
    signerFiscalCode: user.signerFiscalCode ?? "",
    counterSignerFiscalCode: user.counterSignerFiscalCode ?? "",
    active: user.active,
    partitionId: user.partition.id,
    companyId: user.company.id,
    roleIds: user.roles.map((role) => role.id),
    groupIds: user.groups.map((group) => group.id),
  };
}

export function AdminUsersPanel() {
  const [users, setUsers] = useState<ApplicationUser[]>([]);
  const [options, setOptions] = useState<OrganizationOptions>(emptyOptions);
  const [query, setQuery] = useState("");
  const [editingId, setEditingId] = useState<string | undefined>();
  const [form, setForm] = useState<ApplicationUserRequest>(emptyUser(emptyOptions));
  const [status, setStatus] = useState("Loading users...");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (nextQuery: string) => {
    setError(null);
    const [loadedOptions, userPage] = await Promise.all([fetchOrganizationOptions(), fetchUsers(nextQuery)]);
    setOptions(loadedOptions);
    setUsers(userPage.items);
    setForm((current) => current.partitionId ? current : emptyUser(loadedOptions));
    setStatus(`${userPage.total} users found`);
  }, []);

  useEffect(() => {
    load("").catch((reason: Error) => {
      setError(reason.message);
      setStatus("Unable to load administrative users");
    });
  }, [load]);

  const selectedUser = useMemo(() => users.find((user) => user.id === editingId), [editingId, users]);

  function edit(user: ApplicationUser) {
    setEditingId(user.id);
    setForm(fromUser(user));
  }

  function startNew() {
    setEditingId(undefined);
    setForm(emptyUser(options));
  }

  function toggleSelection(field: "roleIds" | "groupIds", id: string) {
    setForm((current) => ({
      ...current,
      [field]: current[field].includes(id) ? current[field].filter((value) => value !== id) : [...current[field], id],
    }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    try {
      await saveUser(form, editingId);
      setStatus(editingId ? "User updated" : "User created");
      await load(query);
      startNew();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to save user");
    }
  }

  async function changeActive(user: ApplicationUser) {
    setError(null);
    try {
      await setUserActive(user.id, !user.active);
      await load(query);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Unable to update active state");
    }
  }

  return (
    <div className="admin-grid">
      <section className="card admin-wide">
        <div className="section-title">
          <div>
            <h1>Utenti</h1>
            <p>{status}</p>
          </div>
          <button className="primary" onClick={startNew}>Nuovo</button>
        </div>
        <form className="search-row" onSubmit={(event) => { event.preventDefault(); load(query).catch((reason: Error) => setError(reason.message)); }}>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Cerca per utente, nome, cognome o codice fiscale" />
          <button className="primary" type="submit">Cerca</button>
        </form>
        {error ? <p className="inline-error" role="alert">{error}</p> : null}
        <div className="table-wrap">
          <table>
            <thead><tr><th>Azione</th><th>Utente</th><th>Nome</th><th>Partizione</th><th>Azienda</th><th>Ruoli</th><th>Gruppi</th><th>Attivo</th></tr></thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td><button onClick={() => edit(user)}>Modifica</button></td>
                  <td>{user.username}<small>{user.oidcSubject}</small></td>
                  <td>{user.lastName} {user.firstName}<small>{user.email}</small></td>
                  <td>{user.partition.code}</td>
                  <td>{user.company.code}</td>
                  <td>{user.roles.map((role) => role.code).join(", ")}</td>
                  <td>{user.groups.map((group) => group.code).join(", ")}</td>
                  <td><button onClick={() => changeActive(user)}>{user.active ? "Disattiva" : "Attiva"}</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card">
        <h2>{selectedUser ? "Modifica utente" : "Nuovo utente"}</h2>
        <form className="admin-form" onSubmit={submit}>
          <label>Utente<input required value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} /></label>
          <label>Identificativo OIDC<input required value={form.oidcSubject} onChange={(event) => setForm({ ...form, oidcSubject: event.target.value })} /></label>
          <label>Cognome<input required value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} /></label>
          <label>Nome<input required value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} /></label>
          <label>Email<input value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
          <label>Codice fiscale<input value={form.fiscalCode} onChange={(event) => setForm({ ...form, fiscalCode: event.target.value })} /></label>
          <label>CF firmatario<input value={form.signerFiscalCode} onChange={(event) => setForm({ ...form, signerFiscalCode: event.target.value })} /></label>
          <label>CF controfirmatario<input value={form.counterSignerFiscalCode} onChange={(event) => setForm({ ...form, counterSignerFiscalCode: event.target.value })} /></label>
          <label>Partizione<select required value={form.partitionId} onChange={(event) => setForm({ ...form, partitionId: event.target.value })}>{options.partitions.map(option)}</select></label>
          <label>Azienda<select required value={form.companyId} onChange={(event) => setForm({ ...form, companyId: event.target.value })}>{options.companies.map(option)}</select></label>
          <fieldset><legend>Ruoli</legend>{options.roles.map((role) => checkbox(role, form.roleIds, () => toggleSelection("roleIds", role.id)))}</fieldset>
          <fieldset><legend>Gruppi</legend>{options.groups.map((group) => checkbox(group, form.groupIds, () => toggleSelection("groupIds", group.id)))}</fieldset>
          <label className="checkbox-line"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /> Attivo</label>
          <button className="primary" type="submit">Conferma</button>
        </form>
      </section>
    </div>
  );
}

function option(item: Option) {
  return <option key={item.id} value={item.id}>{item.code} - {item.name}</option>;
}

function checkbox(item: Option, selected: string[], onChange: () => void) {
  return <label className="checkbox-line" key={item.id}><input type="checkbox" checked={selected.includes(item.id)} onChange={onChange} /> {item.code}</label>;
}
