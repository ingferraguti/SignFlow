create table fse_document_types (
    code varchar(3) primary key,
    display_name varchar(120) not null,
    description varchar(500) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_fse_document_type_code check (code ~ '^[A-Z]{3}$')
);

insert into fse_document_types (code, display_name, description) values
('WOR', 'Documento di workflow', 'Documento relativo a un workflow clinico.'),
('REF', 'Referto', 'Qualsiasi tipologia di referto.'),
('LDO', 'Lettera di dimissione', 'Lettera di dimissione ospedaliera o non ospedaliera.'),
('RIC', 'Richiesta', 'Richiesta clinica, prescrizione o richiesta di consulto.'),
('SUM', 'Sommario', 'Sommario clinico, incluso il profilo sanitario sintetico.'),
('TAC', 'Taccuino', 'Documento trasmesso nel taccuino dall’assistito.'),
('PRS', 'Prescrizione', 'Prescrizione condivisa dal Sistema TS.'),
('PRE', 'Prestazione', 'Prestazione erogata condivisa dal Sistema TS.'),
('ESE', 'Esenzione', 'Documento relativo a esenzioni.'),
('PDC', 'Piano di cura', 'Piano terapeutico condiviso dal Sistema TS.'),
('VAC', 'Vaccino', 'Scheda o certificato vaccinale.'),
('CER', 'Certificato DGC', 'Documento associato a Digital Green Certificate.'),
('VRB', 'Verbale', 'Verbale clinico, incluso il verbale di pronto soccorso.'),
('CON', 'Consenso', 'Documento di consenso clinico.'),
('CNT', 'Controllo clinico', 'Documento che descrive un controllo clinico.'),
('CRT', 'Certificato amministrativo', 'Certificato amministrativo generico.'),
('LET', 'Lettera', 'Comunicazione formale di tipo sanitario.'),
('PRO', 'Promemoria', 'Promemoria sanitario, di prescrizione o appuntamento.'),
('COL', 'Collezione documentale', 'Insieme aggregato di documenti correlati.');

update reports
set document_type = case
    when upper(document_type) like '%LDO%' then 'LDO'
    when upper(document_type) like '%VRB%' then 'VRB'
    else 'REF'
end;

alter table reports
    add constraint fk_reports_fse_document_type
    foreign key (document_type) references fse_document_types(code);

create table source_system_fse_document_types (
    source_system_id uuid not null references source_systems(id) on delete cascade,
    document_type_code varchar(3) not null references fse_document_types(code),
    cda_injection_enabled boolean not null default false,
    updated_at timestamptz not null default now(),
    primary key (source_system_id, document_type_code)
);

insert into source_system_fse_document_types (source_system_id, document_type_code, cda_injection_enabled)
select source_system_id, document_type, bool_or(create_cda)
from reports
group by source_system_id, document_type;

alter table clinical_documents
    add column document_type_code varchar(3),
    add column cda_injection_status varchar(30) not null default 'NOT_REQUESTED';

update clinical_documents d
set document_type_code = r.document_type
from reports r
where r.id = d.report_id;

alter table clinical_documents
    alter column document_type_code set not null,
    add constraint fk_clinical_documents_fse_document_type
        foreign key (document_type_code) references fse_document_types(code),
    add constraint chk_clinical_document_cda_status
        check (cda_injection_status in ('NOT_REQUESTED', 'PENDING_CDA', 'INJECTED', 'FAILED'));

create index idx_clinical_documents_type on clinical_documents(document_type_code);
create index idx_source_system_fse_types on source_system_fse_document_types(document_type_code);

create or replace function default_clinical_document_type()
returns trigger language plpgsql as $$
begin
    if new.document_type_code is null then
        select document_type into new.document_type_code from reports where id=new.report_id;
    end if;
    return new;
end;
$$;

create trigger trg_default_clinical_document_type
before insert on clinical_documents
for each row execute function default_clinical_document_type();
