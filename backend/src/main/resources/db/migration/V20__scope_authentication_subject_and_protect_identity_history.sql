-- Authentication subjects are only unique within their issuing realm. Different
-- LDAP/OIDC/CIE/SPID issuers may legitimately produce the same subject value.
alter table application_users drop constraint if exists application_users_oidc_subject_key;

create or replace function guard_natural_person_identity_event_mutation()
returns trigger language plpgsql as $$
begin
    raise exception 'Natural-person identity events are append-only' using errcode = '42501';
end;
$$;

create trigger trg_guard_natural_person_identity_event_mutation
before update or delete on natural_person_identity_events
for each row execute function guard_natural_person_identity_event_mutation();
