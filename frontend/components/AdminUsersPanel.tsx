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
import { useUiTexts } from "./UiTextProvider";

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
    identifierScheme: "IT_TAX_CODE", issuingCountry: "IT", identifierIssuer: "AGENZIA_ENTRATE",
    personalIdentifier: "", authenticationIssuer: "legacy://signflow",
    authenticationMethod: "OIDC", identityCorrectionReason: "",
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
    identifierScheme: user.identifierScheme ?? "IT_TAX_CODE",
    issuingCountry: user.issuingCountry ?? "IT",
    identifierIssuer: user.identifierIssuer ?? "AGENZIA_ENTRATE",
    personalIdentifier: user.personalIdentifier ?? user.signerFiscalCode ?? user.fiscalCode ?? "",
    authenticationIssuer: user.authenticationIssuer ?? "legacy://signflow",
    authenticationMethod: user.authenticationMethod ?? "OIDC",
    identityCorrectionReason: "",
    active: user.active,
    partitionId: user.partition.id,
    companyId: user.company.id,
    roleIds: user.roles.map((role) => role.id),
    groupIds: user.groups.map((group) => group.id),
  };
}

export function AdminUsersPanel() {
  const { text } = useUiTexts();
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
    let active = true;
    Promise.all([fetchOrganizationOptions(), fetchUsers("")])
      .then(([loadedOptions, userPage]) => {
        if (!active) return;
        setOptions(loadedOptions);
        setUsers(userPage.items);
        setForm((current) => current.partitionId ? current : emptyUser(loadedOptions));
        setStatus(`${userPage.total} users found`);
      })
      .catch((reason: Error) => {
        if (!active) return;
        setError(reason.message);
        setStatus("Unable to load administrative users");
      });
    return () => { active = false; };
  }, []);

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
          <button className="primary" onClick={startNew}>{text("button.newUser")}</button>
        </div>
        <form className="search-row" onSubmit={(event) => { event.preventDefault(); load(query).catch((reason: Error) => setError(reason.message)); }}>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Cerca per utente, nome, cognome o codice fiscale" />
          <button className="primary" type="submit">{text("button.search")}</button>
        </form>
        {error ? <p className="inline-error" role="alert">{error}</p> : null}
        <div className="table-wrap">
          <table>
            <thead><tr><th>Azione</th><th>Account</th><th>Persona naturale</th><th>Nome</th><th>Partizione</th><th>Azienda</th><th>Ruoli</th><th>Gruppi</th><th>Attivo</th></tr></thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td><button onClick={() => edit(user)}>{text("button.edit")}</button></td>
                  <td>{user.username}<small>{user.oidcSubject}</small></td>
                  <td>{user.identifierScheme}<small>{user.issuingCountry} · {user.personalIdentifier}</small><small>{user.naturalPersonId}</small></td>
                  <td>{user.lastName} {user.firstName}<small>{user.email}</small></td>
                  <td>{user.partition.code}</td>
                  <td>{user.company.code}</td>
                  <td>{user.roles.map((role) => role.code).join(", ")}</td>
                  <td>{user.groups.map((group) => group.code).join(", ")}</td>
                  <td><button onClick={() => changeActive(user)}>{text(user.active ? "button.deactivate" : "button.activate")}</button></td>
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
          <label>Subject autenticazione<input required value={form.oidcSubject} onChange={(event) => setForm({ ...form, oidcSubject: event.target.value })} /></label>
          <label>Issuer autenticazione<input required value={form.authenticationIssuer} onChange={(event) => setForm({ ...form, authenticationIssuer: event.target.value })} /></label>
          <label>Metodo autenticazione<select value={form.authenticationMethod} onChange={(event) => setForm({ ...form, authenticationMethod: event.target.value })}><option>OIDC</option><option>LDAP</option><option>SPID</option><option>CIE</option><option>EIDAS</option></select></label>
          <label>Cognome<input required value={form.lastName} onChange={(event) => setForm({ ...form, lastName: event.target.value })} /></label>
          <label>Nome<input required value={form.firstName} onChange={(event) => setForm({ ...form, firstName: event.target.value })} /></label>
          <label>Email<input value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
          <fieldset><legend>Identità della persona naturale</legend>
            <label>Schema identificativo<select value={form.identifierScheme} onChange={(event) => { const identifierScheme = event.target.value; setForm({ ...form, identifierScheme, issuingCountry: identifierScheme === "IT_TAX_CODE" ? "IT" : form.issuingCountry, identifierIssuer: identifierScheme === "IT_TAX_CODE" ? "AGENZIA_ENTRATE" : form.identifierIssuer }); }}><option>IT_TAX_CODE</option><option>EIDAS_PERSON_IDENTIFIER</option><option>NATIONAL_ID</option></select></label>
            <label>Paese emittente<input required maxLength={2} pattern="[A-Za-z]{2}" value={form.issuingCountry} onChange={(event) => setForm({ ...form, issuingCountry: event.target.value.toUpperCase() })} /></label>
            <label>Autorità emittente<input required value={form.identifierIssuer} onChange={(event) => setForm({ ...form, identifierIssuer: event.target.value })} /></label>
            <label>Identificativo personale<input required value={form.personalIdentifier} onChange={(event) => setForm({ ...form, personalIdentifier: event.target.value })} /></label>
          </fieldset>
          {selectedUser ? <label>Motivazione correzione identità<textarea minLength={10} value={form.identityCorrectionReason} onChange={(event) => setForm({ ...form, identityCorrectionReason: event.target.value })} /><small>Obbligatoria solo se l’identificativo collega il profilo a una persona diversa.</small></label> : null}
          <label>Partizione<select required value={form.partitionId} onChange={(event) => setForm({ ...form, partitionId: event.target.value })}>{options.partitions.map(option)}</select></label>
          <label>Azienda<select required value={form.companyId} onChange={(event) => setForm({ ...form, companyId: event.target.value })}>{options.companies.map(option)}</select></label>
          <fieldset><legend>Ruoli</legend>{options.roles.map((role) => checkbox(role, form.roleIds, () => toggleSelection("roleIds", role.id)))}</fieldset>
          <fieldset><legend>Gruppi</legend>{options.groups.map((group) => checkbox(group, form.groupIds, () => toggleSelection("groupIds", group.id)))}</fieldset>
          <label className="checkbox-line"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /> Attivo</label>
          <button className="primary" type="submit">{text("button.confirm")}</button>
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
