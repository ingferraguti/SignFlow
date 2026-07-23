create table admin_ui_texts (
    text_key varchar(100) primary key,
    text_value varchar(300) not null,
    updated_at timestamptz not null default now()
);

insert into admin_ui_texts (text_key, text_value) values
('menu.home', 'Home'),
('menu.system', 'Stato sistema'),
('menu.reports', 'Referti'),
('menu.signature', 'Firma'),
('menu.monitoring', 'Monitoraggio'),
('menu.configuration', 'Configurazione'),
('button.newUser', 'Nuovo utente'),
('button.search', 'Cerca'),
('button.edit', 'Modifica'),
('button.activate', 'Attiva'),
('button.deactivate', 'Disattiva'),
('button.confirm', 'Conferma'),
('button.newOrganization', 'Nuovo'),
('button.saveTexts', 'Salva testi');
