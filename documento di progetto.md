# Documento di progetto — H-Sign / Clinical Signing Hub

## Indice

1. [Identità del progetto](#1-identità-del-progetto)
2. [Obiettivi e perimetro](#2-obiettivi-e-perimetro)
3. [Componenti open source e integrazioni esterne](#3-componenti-open-source-e-integrazioni-esterne)
4. [Architettura funzionale](#4-architettura-funzionale)
5. [Eventi, audit e analytics](#5-eventi-audit-e-analytics)
6. [Modello di dominio](#6-modello-di-dominio)
7. [Macchina a stati del referto](#7-macchina-a-stati-del-referto)
8. [Pipeline configurabile per sistema erogante](#8-pipeline-configurabile-per-sistema-erogante)
9. [Applicazione firmatario](#9-applicazione-firmatario)
10. [Applicazione amministrativa](#10-applicazione-amministrativa)
11. [API REST](#11-api-rest)
12. [Sicurezza, privacy e conformità](#12-sicurezza-privacy-e-conformità)
13. [Backlog funzionale](#13-backlog-funzionale)
14. [Piano di sviluppo consigliato](#14-piano-di-sviluppo-consigliato)
15. [Prompt di sviluppo riutilizzabili](#15-prompt-di-sviluppo-riutilizzabili)
16. [Decisioni architetturali sintetiche](#16-decisioni-architetturali-sintetiche)
17. [Fonte e stato del documento](#17-fonte-e-stato-del-documento)

## 1. Identità del progetto

### 1.1 Nome

- **Nome di progetto:** H-Sign.
- **Nomi funzionali provvisori:** Clinical Signing Hub, Referti Signing Hub.

### 1.2 Definizione

H-Sign è un middleware sanitario open source per la firma digitale remota, il governo dei referti e la tracciabilità dei documenti clinici.

Il sistema riceve referti da applicativi clinici minori, cartelle specialistiche, sistemi legacy, API o flussi HL7; li normalizza e li presenta ai firmatari; consente la firma singola o massiva tramite provider esterni; registra ogni passaggio; produce eventi per audit e analisi; predispone le integrazioni con FSE 2.0 e conservazione sostitutiva.

Il prodotto non sostituisce LIS, RIS o CCE completi. Si colloca come strato trasversale e punto unico di firma per applicativi privi di una funzione di firma o di un workflow documentale maturo.

### 1.3 Posizionamento

> Open source middleware per firma remota, governo dei referti e tracciabilità dei documenti clinici.

Il valore del progetto non consiste nel ricostruire un LIS o un RIS, ma nel creare un hub aperto di firma e governo documentale clinico per:

- applicativi minori;
- cartelle specialistiche;
- verticali legacy;
- ambulatoriali;
- consulenze;
- lettere;
- verbali;
- moduli clinici;
- referti non strutturati;
- integrazioni documentali legacy.

### 1.4 Visione del flusso completo

Il flusso obiettivo è:

```text
ricezione documento
→ normalizzazione metadati
→ firma
→ archiviazione
→ FSE 2.0
→ conservazione
→ consultazione controllata
```

Non risulta disponibile un singolo prodotto open source già completo per l'intera catena:

```text
HL7 → referto → CDA2 → firma remota → FSE 2.0 → conservazione
```

H-Sign deve quindi essere costruito come orchestratore modulare che integra componenti open source e servizi esterni mediante adapter.

## 2. Obiettivi e perimetro

### 2.1 Obiettivi principali

1. Offrire un punto unico di firma remota per documenti clinici provenienti da sistemi diversi.
2. Consentire firma singola e firma massiva, con esito registrato per ciascun documento.
3. Rendere osservabili tempi, errori, code, retry e SLA dell'intero processo.
4. Monitorare qualità ed errori dei flussi HL7 e dei relativi metadati.
5. Gestire documenti già pronti, PDF, CDA esistenti, HL7 da trasformare e documenti con metadati da normalizzare.
6. Integrare validazione e invio a FSE 2.0.
7. Preparare pacchetti e metadati per un conservatore accreditato, senza assumere il ruolo di conservatore.
8. Fornire un repository documentale clinico aziendale leggero e complementare ai grandi DSE.
9. Garantire audit, protezione dei dati, controllo degli accessi e tracciabilità.
10. Rendere il comportamento configurabile per sistema erogante, evitando regole hard-coded.

### 2.2 Funzioni centrali

#### Punto unico di firma remota

Il sistema raccoglie i documenti da firmare provenienti da più applicativi e associa correttamente:

- utente applicativo;
- firmatario clinico;
- account di dominio;
- account di firma remota;
- provider di firma.

#### Firma massiva

La firma massiva deve includere:

- code di firma;
- lotti o batch;
- priorità;
- retry;
- stati dettagliati;
- cruscotto con documenti da firmare, firmati, scaduti o in errore;
- revisione preventiva dell'elenco;
- esito per ogni singolo documento.

Un batch non è un'operazione indivisibile: il provider può firmare alcuni documenti e fallirne altri. Servono pertanto uno stato globale del batch e uno stato distinto per ogni documento.

#### Analisi di tempestività

Devono essere misurabili:

- tempo dalla produzione del referto alla disponibilità per la firma;
- tempo di presa in carico;
- tempo di firma;
- tempo di invio a FSE;
- tempo di invio in conservazione;
- SLA per unità operativa;
- SLA per medico;
- SLA per applicativo;
- SLA per tipologia documentale.

#### Analisi dei flussi HL7

Il monitoraggio deve coprire:

- flussi MDM, ORU e ADT;
- errori di parsing o trattamento;
- referti duplicati;
- referti senza firmatario;
- mismatch tra paziente ed episodio;
- problemi nei segmenti PV1, TX e nei risultati;
- qualità e completezza dei metadati.

#### CDA2 quando necessario

Il CDA2 non deve essere obbligatorio per ogni ingresso. H-Sign deve accettare:

- un documento già pronto;
- un PDF da firmare;
- un CDA già prodotto;
- un HL7 da trasformare in CDA;
- un documento accompagnato da metadati da normalizzare.

#### Gateway FSE 2.0

Il gateway deve gestire:

- validazione;
- invio;
- esiti;
- retry;
- tracciamento degli errori;
- riconciliazione tra repository e documento firmato.

#### Conservazione

H-Sign non deve diventare un conservatore. Deve invece:

- generare pacchetti e metadati;
- inviare i pacchetti tramite API a un conservatore accreditato;
- conservare e correlare le ricevute e gli esiti.

### 2.3 Fuori dal primo MVP

Sono rimandati a fasi successive:

- repository clinico completo;
- Privacy & Access Layer evoluto;
- consensi e oscuramenti completi;
- consultazione clinica estesa;
- integrazioni reali con ogni provider di firma;
- invio FSE reale e conservazione reale;
- OpenSearch e ClickHouse prima della stabilizzazione degli eventi.

Il progetto deve risultare utile già con Signing Hub e Document Gateway, senza trasformarsi subito in un grande DSE.

## 3. Componenti open source e integrazioni esterne

### 3.1 Integrazione HL7

La base proposta è **HAPI HL7v2**, open source e maturo per parsing e comunicazione MLLP su HL7 2.x.

Un motore come Mirth/NextGen è considerato meno adatto come nuova base perché, dalla versione 4.6, è passato a un modello proprietario.

### 3.2 CDA2 e FSE 2.0

Occorre partire dai repository ufficiali del Ministero della Salute:

- `ministero-salute/it-fse-support`;
- `ministero-salute/it-fse-catalogs`;
- `ministero-salute/it-fse-gtw-tools`;
- `ministero-salute/it-fse-accreditamento`.

Questi repository mettono a disposizione documentazione, XSD, Schematron, dizionari, esempi CDA, strumenti per JWT/PDF/validazione e materiale per l'accreditamento.

Per i profili CDA2 italiani vanno considerati anche i repository di HL7 Italia, inclusi i profili per lab report e altri documenti clinici. È inoltre citato `hl7-it/cda2fhir` per il mapping da CDA a FHIR.

### 3.3 Firma digitale

Per la parte crittografica è indicato **EU DSS — Digital Signature Service**, progetto Java open source orientato a eIDAS e AdES, adatto alla creazione e validazione di firme avanzate e utilizzabile come mattone per PAdES, CAdES e XAdES.

### 3.4 Provider di firma remota

I provider espongono spesso API proprietarie. Non è realistico attendersi una singola libreria universale. Il core deve quindi definire un contratto comune e fornire adapter separati, per esempio per:

- Aruba;
- Namirial;
- InfoCert;
- Intesi;
- altri provider.

Le specificità del provider non devono essere cablate nell'interfaccia utente o nel dominio centrale.

### 3.5 Conservazione sostitutiva

È raro trovare uno stack open source completo e conforme per la conservazione. L'approccio previsto è preparare pacchetti e metadati e integrarli tramite API con un conservatore accreditato.

### 3.6 Analytics e BI

- **ClickHouse:** analytics real-time su grandi volumi, inclusi tempi di firma, volumi per UO, SLA, ritardi, errori FSE, throughput e trend.
- **Apache Superset:** opzione moderna, leggera e developer-friendly, adatta a ClickHouse e PostgreSQL.
- **Knowage:** opzione coerente con contesti PA e healthcare italiani, orientata a scenari enterprise.

Per una prima implementazione open e rapida è suggerita la combinazione Superset + ClickHouse. Knowage rimane un'opzione per contesti PA/enterprise.

## 4. Architettura funzionale

### 4.1 Moduli

#### Modulo A — Signing Hub

- firma singola;
- firma massiva;
- code;
- provider;
- lotti;
- retry;
- audit delle operazioni di firma.

#### Modulo B — Document Gateway

- HL7;
- CDA2;
- PDF/A;
- validazione e invio FSE;
- integrazione con conservazione.

#### Modulo C — Clinical Document Repository

- archivio dei documenti firmati;
- versioni;
- metadati;
- correlazione con episodio e paziente;
- provenienza del documento.

Il repository è leggero e complementare ai DSE esistenti, non un sostituto.

#### Modulo D — Privacy & Access Layer

- consensi;
- oscuramenti;
- motivazione dell'accesso;
- profili autorizzativi;
- logging delle consultazioni;
- emergency access.

#### Modulo E — Analytics

- SLA di firma;
- ritardi;
- qualità dei flussi;
- volumi;
- errori FSE;
- documenti non firmati;
- documenti non conservati.

Il modulo Analytics non deve essere implementato dentro l'applicazione principale. L'applicazione produce eventi puliti e gli strumenti BI li consumano.

### 4.2 Architettura tecnologica suggerita

- Java o Kotlin;
- Spring Boot;
- HAPI HL7v2;
- EU DSS;
- validatori FSE ufficiali;
- PostgreSQL;
- object storage S3-compatible, preferibilmente MinIO;
- RabbitMQ o Kafka per le code e gli eventi;
- ClickHouse per analytics;
- OpenSearch opzionale;
- Superset o Knowage per BI;
- adapter per provider di firma, FSE e conservazione.

### 4.3 Persistenza ibrida

#### PostgreSQL

Per i dati transazionali e lo stato corrente:

- job di firma;
- utenti tecnici;
- configurazioni;
- stati;
- audit applicativo essenziale;
- provider;
- code;
- retry;
- metadati dei documenti.

#### Object storage S3-compatible

Per i payload pesanti:

- HL7 raw;
- CDA2;
- PDF;
- PDF/A;
- XML firmati;
- documenti firmati;
- ricevute FSE;
- pacchetti e ricevute di conservazione.

#### OpenSearch

Opzionale dalla seconda fase, per:

- ricerca su messaggi HL7;
- errori;
- log applicativi;
- payload semi-strutturati;
- query tecniche su paziente, episodio e documento;
- troubleshooting.

#### ClickHouse

Per eventi denormalizzati e analytics real-time:

- tempi di firma;
- volumi per UO;
- SLA;
- ritardi;
- errori FSE;
- throughput;
- trend.

### 4.4 Flusso tecnico minimo

```text
HL7 listener
→ creazione job di firma
→ provider di firma
→ salvataggio documento
→ invio FSE/conservazione
→ produzione eventi analitici
```

Ogni passaggio salva lo stato corrente in PostgreSQL, gli artefatti nell'object storage e gli eventi in ClickHouse/OpenSearch quando questi componenti saranno attivati.

## 5. Eventi, audit e analytics

### 5.1 Eventi di alto livello

Il documento individua i seguenti eventi di processo:

- `REFERT_RECEIVED`;
- `DOCUMENT_NORMALIZED`;
- `SIGNATURE_REQUESTED`;
- `SIGNED`;
- `FSE_SENT`;
- `FSE_ACCEPTED`;
- `CONSERVATION_SENT`;
- `ERROR`.

Per l'implementazione applicativa viene proposta una nomenclatura più esplicita:

- `REPORT_RECEIVED`;
- `REPORT_PARSED`;
- `REPORT_READY_TO_SIGN`;
- `REPORT_PREVIEWED`;
- `SIGNATURE_BATCH_CREATED`;
- `SIGNATURE_REQUESTED`;
- `REPORT_SIGNED`;
- `SIGNATURE_FAILED`;
- `FSE_SENT`;
- `FSE_ACCEPTED`;
- `CONSERVATION_SENT`;
- `ERROR`.

### 5.2 Regole sugli eventi

- Ogni cambio stato del referto produce un evento append-only.
- Ogni operazione utente rilevante produce un evento.
- Gli eventi sono inizialmente salvati in PostgreSQL.
- Devono poter essere pubblicati in futuro verso ClickHouse e OpenSearch.
- Il backend espone interfacce come `EventPublisher` e `AnalyticsEventWriter`.
- Gli eventi non contengono dati sensibili non necessari.
- Si preferiscono identificativi tecnici e metadati minimi.

### 5.3 Eventi di audit

Devono essere auditati almeno:

- ricerche;
- apertura dell'anteprima;
- accesso al referto;
- avvio della firma;
- autenticazione al provider;
- completamento della firma;
- errori;
- invio FSE;
- invio in conservazione;
- modifiche alle configurazioni;
- import utenti;
- export amministrativi.

## 6. Modello di dominio

### 6.1 Organizzazione e accessi

#### `Partition`

Dominio logico o tenant organizzativo. Esempi presenti nel materiale: `SIADOM`, `POLICLINICO`.

#### `Company`

Azienda sanitaria o organizzazione. Esempi presenti nel materiale: `AUSL`, `AOU`, `NOS`.

#### `User`

Identità applicativa con:

- username;
- dominio;
- cognome;
- nome;
- codice fiscale;
- cellulare;
- email;
- descrizione;
- stato attivo;
- partizione;
- ruoli;
- gruppi;
- mapping eventuale con il firmatario;
- codice fiscale del controfirmatario, inizialmente anche solo informativo.

#### `Role`

Ruoli iniziali:

- `Administrator`;
- `Firmatario`.

Ruoli futuri possibili:

- Operatore tecnico;
- Auditor;
- Responsabile UO;
- Amministratore di partizione.

#### `Group`

Gruppo funzionale che può collegare utenti a provider, partizioni, regole o sistemi. Esempio: `Firmatari Aruba`.

#### `Signer` / `Firmatario`

Persona abilitata alla firma clinica. Può avere più account tecnici e più provider di firma.

#### `CounterSignerRule`

Regola per controfirma, doppia firma, delega, validazione o workflow con responsabile. Nel primo MVP può essere rappresentata dal solo codice fiscale del controfirmatario su `User`, ma il modello deve potersi evolvere.

### 6.2 Firma

#### `SignatureProvider`

Configurazione del provider di firma:

- nome provider;
- endpoint;
- modalità di autenticazione;
- parametri tecnici;
- stato;
- capacità supportate.

#### `SignatureAccount`

Associazione tra utente/firmatario e provider:

- username del provider;
- alias del certificato;
- modalità o tipo di credenziale;
- modalità di firma;
- stato di abilitazione;
- scadenza.

Non contiene password persistenti.

#### `SignatureBatch`

Operazione di firma massiva con:

- utente che l'ha avviata;
- data;
- provider;
- numero di documenti;
- stato globale;
- esito complessivo;
- elenco dei documenti inclusi, esclusi, firmati o falliti.

#### `SignatureAttempt` / `FirmaDocumento`

Tentativo di firma di un singolo referto, dentro o fuori un batch:

- stato;
- numero di tentativi;
- errore provider;
- timestamp della richiesta;
- timestamp di completamento;
- identificativi restituiti dal provider;
- riferimento al batch, se presente.

### 6.3 Sorgenti, documenti e pazienti

#### `SourceSystem`

Sistema erogante, oggetto centrale della configurazione. Campi:

- codice;
- codice azienda;
- descrizione;
- tipo CDA;
- attivo;
- `pdfA3Conversion` / conversione PDF/A3;
- `visibleSignature` / firma visibile;
- `multipleSignature` / firma multipla;
- `sendUnsigned` / invia non firmati;
- `createCda` / crea CDA;
- `passthrough`.

Il comportamento della pipeline deve derivare da questi parametri.

#### `FseFacilityMapping`

Mapping dei presidi eroganti verso FSE:

- codice del presidio erogante;
- descrizione del presidio;
- azienda o codice azienda;
- unità operativa;
- codice UO;
- descrizione UO;
- sistema erogante;
- reparto;
- stato attivo.

#### `Report` / `Referto`

Oggetto funzionale da firmare o già firmato:

- UUID tecnico;
- ID interno;
- ID esterno;
- ID FSE;
- paziente;
- firmatario;
- stato;
- data modifica;
- data referto;
- data firma;
- sistema erogante;
- applicativo sorgente;
- tipo referto;
- reparto o struttura erogante;
- episodio;
- documento associato;
- CDA eventuale;
- PDF eventuale;
- errori;
- multifirma;
- warning di conversione;
- referto remoto;
- passthrough;
- altri flag tecnici.

Il referto costituisce la sintesi tra documento clinico, paziente, workflow di firma, sistema sorgente e stato tecnico.

#### `PatientMetadata`

Metadati del paziente:

- ID paziente;
- nome;
- cognome;
- sesso;
- data di nascita;
- luogo di nascita;
- codice fiscale.

Questi dati devono essere minimizzati e protetti.

#### `ClinicalDocument` / `DocumentoClinico`

Artefatto tecnico associato al referto:

- PDF;
- PDF/A;
- CDA2;
- XML;
- documento firmato;
- ricevuta FSE;
- ricevuta di conservazione.

Il contenuto risiede nell'object storage; PostgreSQL contiene soltanto metadati, riferimenti, hash, tipo, versione e correlazioni.

#### `Hl7Message` / `MessaggioHL7`

Messaggio ricevuto o prodotto:

- direzione input/output;
- stato processato/scartato;
- sistema sorgente;
- tipo messaggio;
- timestamp;
- correlation ID;
- errore;
- paziente;
- episodio;
- documento generato;
- riferimento al raw payload conservato nell'object storage.

### 6.4 Audit e analytics

#### `AuditEvent`

Evento applicativo append-only con le informazioni necessarie alla tracciabilità legale e operativa.

#### `AnalyticsEvent`

Evento denormalizzato e semplificato per ClickHouse e BI, relativo per esempio a:

- ricezione;
- disponibilità per la firma;
- visualizzazione;
- firma;
- invio FSE;
- accettazione FSE;
- invio in conservazione;
- errore.

### 6.5 Identificativi e indici

- Usare UUID come identificativi tecnici.
- Prevedere indici per codice fiscale del firmatario, ID referto, ID esterno, ID FSE, sistema erogante, stato e data modifica.
- Applicare paginazione obbligatoria alle ricerche.

## 7. Macchina a stati del referto

### 7.1 Stati sintetici mostrati all'utente

- Non firmato;
- Firmato;
- In errore;
- Incompleto.

### 7.2 Stati interni

| Stato | Significato |
|---|---|
| `RECEIVED` | Referto ricevuto tramite HL7 o API. |
| `PARSED` | Metadati estratti. |
| `MISSING_SIGNER` | Firmatario assente o non riconosciuto. |
| `INCOMPLETE` | Mancano dati obbligatori, come paziente, firmatario, documento, tipo, reparto, episodio o codice fiscale. |
| `READY_TO_SIGN` | Documento pronto e firmabile. |
| `PREVIEWED` | Documento visualizzato almeno una volta dal firmatario. |
| `SIGN_BATCH_CREATED` | Referto inserito in un batch. |
| `SIGNING` | Firma in corso. |
| `SIGNED` | Firma completata. |
| `SIGN_ERROR` | Errore durante la firma. |
| `FSE_VALIDATION_ERROR` | Documento firmato ma non valido per FSE 2.0. |
| `FSE_SENT` | Documento inviato al gateway FSE. |
| `FSE_ACCEPTED` | Documento accettato dal FSE. |
| `FSE_REJECTED` | Documento respinto dal FSE. |
| `CONSERVATION_SENT` | Documento inviato in conservazione. |
| `CONSERVATION_ACCEPTED` | Documento preso in carico dal conservatore. |

La UI del firmatario mantiene stati semplici; backend, area admin e dashboard usano gli stati dettagliati per individuare con precisione il punto di blocco.

## 8. Pipeline configurabile per sistema erogante

### 8.1 Principio centrale

H-Sign deve essere configurabile per sistema erogante. Il comportamento non deve essere scritto nel codice per sistemi specifici come `GPI-SP`, `ICW-LDD` o `AURIGALT-SP`: deve derivare dalla configurazione di `SourceSystem` e dai mapping FSE.

Questo principio trasforma il prodotto da piccolo firmatore a middleware riusabile.

### 8.2 Significato dei flag

- `createCda = true`: H-Sign costruisce il CDA2 da HL7, documento e metadati se il CDA non è già presente.
- `passthrough = true`: H-Sign non ricostruisce il documento, ma riceve un artefatto già pronto o quasi pronto e applica controlli minimi.
- `pdfA3Conversion = true`: il documento viene convertito o normalizzato in PDF/A3 prima della firma o dell'invio.
- `visibleSignature = true`: al PDF viene applicata una rappresentazione visibile della firma.
- `multipleSignature = true`: il referto può richiedere più firmatari o un ciclo di controfirma.
- `sendUnsigned = true`: il sistema può trasmettere documenti non firmati soltanto nei casi espressamente configurati e con particolare cautela.

La combinazione `createCda = true` e `passthrough = true` è potenzialmente incoerente e deve produrre almeno un warning di validazione.

### 8.3 Flusso di ingestione

1. Il sistema riceve un messaggio HL7 o una richiesta API da un sistema erogante.
2. Identifica il `SourceSystem`.
3. Carica le regole del sistema erogante.
4. Estrae paziente, episodio, referto, firmatario, reparto, date e documento.
5. Registra un `Hl7Message` come processato o scartato.
6. Crea o aggiorna il `Report`.
7. Se mancano dati obbligatori, imposta `INCOMPLETE` o scarta il messaggio secondo la regola applicabile.
8. Se manca o non viene riconosciuto il firmatario, imposta `MISSING_SIGNER` e mostra il referto nella coda “Referti senza firmatario”.
9. Se `createCda = true` e il CDA non è presente, chiama `CdaBuilder`.
10. Se `passthrough = true`, salta `CdaBuilder`, conserva il documento ricevuto e applica controlli minimi.
11. Se `pdfA3Conversion = true`, chiama `PdfA3Converter`.
12. Se tutto è pronto, imposta `READY_TO_SIGN`.
13. Il firmatario visualizza il documento.
14. Il firmatario firma singolarmente o massivamente.
15. Il sistema registra richiesta, esito del provider e documento firmato.
16. In una fase successiva, il sistema invia a FSE 2.0 e conservazione.
17. Ogni transizione produce `AuditEvent` e `AnalyticsEvent`.

`CdaBuilder` e `PdfA3Converter` devono essere interfacce con implementazioni mock nella prima fase.

## 9. Applicazione firmatario

### 9.1 Struttura generale

La UI è una console operativa per medici e firmatari che cercano, verificano, visualizzano e firmano documenti singolarmente o in massa.

Il mock analizzato mostra:

- menu Home, Referti, Legenda e Informazioni;
- utente corrente e logout;
- contesto applicativo/dominio, per esempio `SIADOM\ferragutim`;
- provider visualizzato, per esempio `ARUBA AUSL`.

Il dominio deve distinguere account applicativo, identità del medico, account di dominio, account remoto e provider.

### 9.2 Lista referti

#### Ricerca semplice

- stato;
- data referto;
- opzione per nascondere referti in errore;
- opzione per nascondere referti incompleti;
- pulsanti Cerca e Annulla.

#### Ricerca avanzata

- cognome;
- nome;
- anteprima;
- tipo;
- reparto;
- intervallo data firma.

I filtri devono essere elaborati dal backend, perché in produzione i referti possono essere migliaia.

#### Tabella

Colonne:

- selezione;
- icona documento/anteprima;
- paziente;
- referto;
- anteprima;
- stato;
- data referto;
- data firma.

La cella paziente mostra:

- nominativo;
- sesso;
- data di nascita;
- luogo di nascita;
- codice fiscale.

La cella referto mostra:

- tipo di documento;
- reparto o struttura erogante.

La colonna anteprima deve rappresentare uno stato o un'azione, per esempio:

- visualizzato;
- non visualizzato;
- anteprima disponibile;
- documento non renderizzabile;
- icona di apertura.

### 9.3 Azioni e firma massiva

- `Firma selezionati` è disabilitato quando non sono selezionate righe.
- `Firma tutti` può essere disponibile sulla ricerca corrente.
- Prima della firma massiva deve sempre comparire una revisione con elenco, numero totale, esclusioni ed eventuali avvisi per documenti incompleti o in errore.

Il wizard segue tre passi:

1. Anteprima;
2. Revisione;
3. Firma.

La firma non parte direttamente dalla lista: il medico deve sapere quali documenti sta firmando, anche in modalità massiva.

La schermata di firma massiva:

- indica provider e account;
- mostra tutti i referti del batch;
- permette di aprire ogni anteprima mediante icona a forma di occhio;
- espone i pulsanti Chiudi e Firma;
- genera un batch collegato a N referti;
- registra gli esiti documento per documento.

### 9.4 Autenticazione provider

Il mock mostra un modale “Autenticazione ARUBA AUSL” con utente e password. Le credenziali:

- non devono essere salvate;
- devono essere usate soltanto per una sessione temporanea;
- possono essere sostituite da username/password, OTP, push, certificato, token o flusso simile a OAuth, secondo il provider.

La UI usa un componente generico e configurabile; non contiene logica Aruba hard-coded.

### 9.5 Dettaglio e firma singola

La pagina “Referto non firmato” mostra:

- dati del paziente;
- dati del referto;
- viewer PDF integrato;
- pulsante Firma referto;
- pulsante Indietro.

Il documento renderizzabile viene recuperato preferibilmente dall'object storage, non dal database relazionale.

### 9.6 Route previste

- `/login` — autenticazione applicativa;
- `/referti` — elenco e ricerca semplice/avanzata;
- `/referti/:id` — dettaglio e firma singola;
- `/firma/batch/:batchId/revisione` — revisione della selezione o di “firma tutti”;
- `/firma/batch/:batchId/autenticazione` — autenticazione provider, eventualmente sostituita da un modale;
- `/firma/batch/:batchId/esito` — riepilogo con documenti firmati, falliti ed esclusi.

### 9.7 Componenti frontend

- `RefertiSearchPanel`;
- `RefertiTable`;
- `ReportPatientCell`;
- `ReportDocumentCell`;
- `ReportDetail`;
- `BatchSignatureWizard`;
- `ProviderAuthModal`;
- `PdfPreviewViewer`;
- `SignatureResultPanel`.

## 10. Applicazione amministrativa

### 10.1 Scopo

Il mock admin porta la specifica funzionale alla versione 0.2 e chiarisce che H-Sign è un sistema di governo della firma digitale, non una sola maschera di firma. L'area amministrativa copre configurazione delle sorgenti, gestione dei firmatari, mapping FSE, monitoraggio dei flussi e diagnostica operativa.

### 10.2 Navigazione

#### Ricerca

- Referti.

#### Monitoraggio

- Messaggi scartati input;
- Messaggi scartati output;
- Messaggi processati input;
- Messaggi processati output;
- Referti senza firmatario.

#### Gestione

- Utenti;
- Gruppi;
- Provider di firma;
- Sistemi eroganti;
- Presidi eroganti;
- Aziende;
- Partizioni.

Le tre aree separano ricerca operativa, monitoraggio tecnico-funzionale e configurazione amministrativa.

### 10.3 Gestione utenti

#### Ricerca e azioni

Filtri:

- utente;
- cognome;
- codice fiscale.

Azioni:

- Nuovo;
- Esporta;
- Importa da SAC;
- Importa da Excel.

L'anagrafica può essere alimentata manualmente, da SAC o da file Excel. Se l'integrazione non è pronta, le funzioni possono inizialmente essere placeholder.

La pagina deve avvisare quando i risultati superano il numero di righe visualizzabili.

#### Tabella utenti

Colonne:

- azione di modifica;
- utente;
- cognome;
- nome;
- codice fiscale;
- partizione;
- ruoli;
- gruppi;
- email;
- attivo.

#### Nuovo/modifica utente

Sezione Dati utente:

- utente obbligatorio;
- attivo;
- cognome;
- nome;
- codice fiscale obbligatorio;
- cellulare;
- email;
- descrizione.

Sezione Controfirmatario:

- codice fiscale.

Sezione Accessi:

- checkbox `Administrator`;
- checkbox `Firmatario`;
- tabella dei gruppi associati;
- pulsante Modifica gruppi.

Footer:

- Elimina;
- Conferma;
- Annulla;
- Applica.

### 10.4 Ricerca referti admin

L'area admin serve a cercare, diagnosticare e comprendere errori e stati, mentre la UI utente serve al firmatario.

#### Ricerca semplice

- ID referto;
- ID esterno;
- periodo di modifica;
- stato.

Regola funzionale: quando sono valorizzati ID referto, ID esterno o ID FSE, questi identificativi puntuali ignorano tutti gli altri filtri.

#### Ricerca avanzata

- ID paziente;
- cognome;
- nome;
- tipo referto;
- firmatario;
- codice fiscale firmatario;
- data referto;
- data firma;
- sistema erogante;
- multifirma;
- warning conversione;
- referto remoto;
- passthrough;
- ID FSE;
- reparto erogante.

#### Tabella referti

Colonne:

- anteprima;
- paziente;
- referto;
- firmatario;
- stato;
- data modifica;
- data referto;
- data firma;
- sistema erogante;
- multifirma;
- warning conversione;
- referto remoto.

La cella paziente mostra nome, cognome, sesso, data e luogo di nascita e codice fiscale. La cella referto mostra tipo e reparto. La cella firmatario mostra nome, cognome e codice fiscale.

Il dettaglio admin deve includere documento, stato, storico eventi, errori, tentativi di firma ed esiti FSE/conservazione.

### 10.5 Sistemi eroganti

#### Ricerca e azioni

Filtri:

- codice;
- codice azienda;
- attivo;
- tipo CDA.

Azioni:

- Nuovo;
- Esporta.

#### Tabella

Colonne:

- modifica;
- codice;
- codice azienda;
- tipo CDA;
- attivo;
- descrizione;
- conversione PDF/A3;
- firma visibile;
- firma multipla;
- invia non firmati;
- crea CDA;
- passthrough.

#### Form e validazioni

Il form Nuovo/Modifica comprende tutti i campi sopra indicati. Codice e codice azienda sono obbligatori. Combinazioni incoerenti, come `createCda = true` insieme a `passthrough = true`, devono essere impedite oppure segnalate chiaramente.

### 10.6 Presidi eroganti e configurazioni FSE

Titolo: “Presidi eroganti”.

Sottotitolo: “Configurazioni per FSE”.

Filtri:

- codice;
- descrizione;
- sistema erogante con autocomplete;
- codice unità operativa.

Azione:

- Nuovo.

Tabella:

- modifica;
- presidio erogante;
- unità operativa;
- sistema erogante;
- reparto.

Il form Nuovo/Modifica include:

- codice presidio;
- descrizione presidio;
- codice azienda;
- codice unità operativa;
- descrizione unità operativa;
- sistema erogante;
- reparto;
- attivo.

Il mapping normalizza i metadati destinati a FSE 2.0. Una configurazione errata può causare rifiuti FSE o documenti formalmente incoerenti.

### 10.7 Monitoraggio flussi

Pagine:

- Messaggi scartati input;
- Messaggi scartati output;
- Messaggi processati input;
- Messaggi processati output;
- Referti senza firmatario.

Filtri comuni:

- periodo;
- sistema erogante;
- tipo messaggio;
- stato;
- correlation ID;
- ID esterno;
- codice fiscale del paziente, quando disponibile.

Colonne:

- timestamp;
- sistema erogante;
- direzione;
- tipo messaggio;
- esito;
- errore sintetico;
- correlation ID;
- ID referto, se presente.

Il dettaglio messaggio mostra:

- metadati;
- errore;
- payload raw mascherato;
- timeline degli eventi.

I dati sensibili non devono comparire nei log applicativi. L'interfaccia admin espone soltanto le informazioni necessarie al troubleshooting.

“Referti senza firmatario” è una coda operativa essenziale: intercetta documenti che non possono essere assegnati perché il firmatario manca o non viene riconosciuto.

## 11. API REST

### 11.1 API applicazione firmatario

| Metodo | Endpoint | Funzione |
|---|---|---|
| `GET` | `/api/reports` | Elenco referti filtrato. |
| `GET` | `/api/reports/{id}` | Dettaglio referto. |
| `GET` | `/api/reports/{id}/preview` | URL temporaneo o stream del PDF. |
| `POST` | `/api/reports/{id}/sign` | Firma singola. |
| `POST` | `/api/signature-batches` | Crea un batch da una selezione o dal filtro “firma tutti”. |
| `GET` | `/api/signature-batches/{id}` | Legge batch e documenti inclusi. |
| `POST` | `/api/signature-batches/{id}/confirm` | Conferma la revisione. |
| `POST` | `/api/signature-batches/{id}/sign` | Avvia la firma massiva. |
| `GET` | `/api/signature-batches/{id}/result` | Restituisce l'esito del batch. |
| `GET` | `/api/signature-providers` | Elenca i provider disponibili per l'utente. |
| `POST` | `/api/signature-providers/{id}/session` | Crea una sessione temporanea con il provider. |

### 11.2 API amministrative

#### Utenti

- `GET /api/admin/users`;
- `POST /api/admin/users`;
- `GET /api/admin/users/{id}`;
- `PUT /api/admin/users/{id}`;
- `DELETE /api/admin/users/{id}`;
- `POST /api/admin/users/import-sac`;
- `POST /api/admin/users/import-excel`;
- `GET /api/admin/users/export`.

#### Referti

- `GET /api/admin/reports`;
- `GET /api/admin/reports/{id}`.

#### Sistemi eroganti

- `GET /api/admin/source-systems`;
- `POST /api/admin/source-systems`;
- `GET /api/admin/source-systems/{id}`;
- `PUT /api/admin/source-systems/{id}`;
- `DELETE /api/admin/source-systems/{id}`;
- `GET /api/admin/source-systems/export`.

#### Mapping FSE

- `GET /api/admin/fse-facility-mappings`;
- `POST /api/admin/fse-facility-mappings`;
- `GET /api/admin/fse-facility-mappings/{id}`;
- `PUT /api/admin/fse-facility-mappings/{id}`;
- `DELETE /api/admin/fse-facility-mappings/{id}`.

#### Monitoraggio

- `GET /api/admin/monitoring/messages`;
- `GET /api/admin/monitoring/messages/{id}`;
- `GET /api/admin/monitoring/reports-without-signer`.

### 11.3 Requisiti trasversali delle API

- paginazione;
- ordinamento;
- filtri validati;
- validazione dell'input;
- autorizzazione per partizione e ruolo;
- audit degli accessi admin;
- rate limit sulle API di ricerca.

## 12. Sicurezza, privacy e conformità

### 12.1 Separazione delle autenticazioni

L'autenticazione applicativa è distinta dall'autenticazione al provider di firma.

### 12.2 Credenziali del provider

- Non salvare password del provider.
- Usare sessioni temporanee.
- Supportare modalità configurabili: password, OTP, push, certificato, token o flussi analoghi a OAuth.
- Non registrare le credenziali nei log o negli eventi.

### 12.3 Autorizzazioni

- Un firmatario può vedere soltanto i referti assegnati a lui.
- Un admin può vedere i referti consentiti dalle sue partizioni e autorizzazioni.
- Gli export massivi sono consentiti soltanto al ruolo admin.
- Le azioni privilegiate devono essere auditabili.

### 12.4 Protezione dei dati

- Minimizzare i dati del paziente.
- Mascherare codice fiscale e dati clinici nei log tecnici.
- Mostrare nel frontend admin soltanto i dati necessari al troubleshooting.
- Mascherare il payload HL7 raw visualizzato.
- Rendere configurabile la retention dei raw HL7.
- Non inserire dati sensibili superflui negli eventi analytics.

### 12.5 Sicurezza applicativa

- validare ogni filtro e input;
- configurare correttamente CSRF e CORS;
- applicare paginazione obbligatoria;
- predisporre rate limit sulle ricerche;
- auditare anteprime, firme, import, export e modifiche di configurazione;
- evitare l'esposizione diretta e permanente degli oggetti nello storage, usando URL temporanei o stream controllati.

## 13. Backlog funzionale

### 13.1 Area utenti

- CRUD utenti;
- ricerca;
- import da SAC;
- import da Excel;
- export;
- attivazione e disattivazione;
- associazione ruoli;
- associazione gruppi;
- mapping del codice fiscale;
- gestione del controfirmatario.

### 13.2 Area referti admin

- ricerca trasversale;
- filtri puntuali e avanzati;
- dettaglio referto;
- visualizzazione documento;
- stato e firmatario;
- sistema erogante;
- flag tecnici;
- storico eventi;
- errori;
- tentativi di firma;
- esiti FSE;
- esiti conservazione.

### 13.3 Sistemi eroganti

- CRUD sorgenti;
- configurazione tipo CDA;
- creazione CDA;
- passthrough;
- conversione PDF/A3;
- firma visibile;
- firma multipla;
- invio di non firmati;
- validazione delle combinazioni di regole.

### 13.4 Mapping FSE

- CRUD presidi;
- mapping aziende;
- mapping unità operative;
- mapping reparti;
- associazione sistemi eroganti;
- validazione dei metadati normalizzati.

### 13.5 Monitoraggio

- messaggi input/output processati;
- messaggi input/output scartati;
- traffico rilevante completo;
- dettaglio degli errori;
- timeline;
- payload mascherato;
- referti senza firmatario;
- SLA e diagnostica operativa.

## 14. Piano di sviluppo consigliato

1. Scheletro frontend admin e firmatario con dati mock.
2. Modello dominio e database PostgreSQL.
3. CRUD utenti, gruppi e partizioni.
4. CRUD sistemi eroganti.
5. CRUD mapping presidi FSE.
6. Ricerca referti admin e firmatario.
7. Firma mock singola e massiva.
8. Pipeline di ingestione HL7 mock.
9. Monitoraggio messaggi e referti senza firmatario.
10. Eventi di audit e analytics.
11. MinIO per i documenti.
12. OpenSearch e ClickHouse soltanto dopo la stabilizzazione degli eventi.

## 15. Prompt di sviluppo riutilizzabili

Questa sezione conserva e organizza i prompt operativi presenti nel documento sorgente.

### 15.1 Contesto base

```text
Stiamo realizzando un'applicazione open source chiamata provvisoriamente Clinical Signing Hub. È un middleware sanitario per ricevere referti da applicativi clinici minori, cartelle specialistiche o flussi HL7, mostrarli ai firmatari, consentire firma digitale remota singola o massiva tramite provider esterni, tracciare gli stati del processo, produrre eventi per analytics e predisporre integrazioni successive con FSE 2.0 e conservazione sostitutiva.

Non deve sostituire LIS/RIS completi, ma servire come punto unico di firma per applicativi non dotati di firma o workflow documentale maturo. L'interfaccia utente comprende lista referti, ricerca semplice/avanzata, anteprima documento, firma singola, firma massiva, autenticazione provider e riepilogo esiti.

I moduli repository clinico completo e privacy/access layer evoluto sono fuori MVP e arriveranno dopo.
```

### 15.2 Frontend firmatario iniziale

```text
Crea una web app React/Next.js con TypeScript per il modulo user di Clinical Signing Hub.

Implementa le pagine: lista referti con ricerca semplice e avanzata, tabella selezionabile, dettaglio referto con viewer PDF mock, wizard firma massiva con step Anteprima/Revisione/Firma e modal autenticazione provider.

Usa dati mock realistici ma fittizi. Non implementare ancora un backend reale.

Struttura il codice in componenti riusabili: RefertiSearchPanel, RefertiTable, ReportDetail, BatchSignatureWizard, ProviderAuthModal, PdfPreviewViewer.

La UI deve essere sobria, adatta a un contesto sanitario, con pulsanti Cerca, Annulla, Firma selezionati, Firma tutti, Firma referto e Indietro.
```

### 15.3 Backend firmatario iniziale

```text
Crea un backend REST per Clinical Signing Hub. Usa Spring Boot oppure NestJS, con modello dominio per Report, PatientMetadata, ClinicalDocument, SignatureProvider, SignatureAccount, SignatureBatch, SignatureAttempt e AuditEvent.

Implementa endpoint per lista referti filtrata, dettaglio referto, creazione batch firma, conferma batch, firma mock ed esito batch. Per ora usa un provider firma mock, senza integrazione reale.

Prevedi gli stati interni RECEIVED, INCOMPLETE, READY_TO_SIGN, PREVIEWED, SIGNING, SIGNED, SIGN_ERROR, FSE_SENT, FSE_ACCEPTED, CONSERVATION_SENT e CONSERVATION_ACCEPTED.

Usa PostgreSQL per i metadati e lascia interfacce per object storage e analytics events.
```

### 15.4 Eventi e analytics

```text
Implementa un sistema di eventi applicativi per Clinical Signing Hub.

Ogni cambio stato del referto e ogni operazione utente deve produrre un evento append-only: REPORT_RECEIVED, REPORT_PARSED, REPORT_READY_TO_SIGN, REPORT_PREVIEWED, SIGNATURE_BATCH_CREATED, SIGNATURE_REQUESTED, REPORT_SIGNED, SIGNATURE_FAILED, FSE_SENT, FSE_ACCEPTED, CONSERVATION_SENT, ERROR.

Gli eventi devono essere salvati in PostgreSQL e pubblicabili in futuro su ClickHouse/OpenSearch. Crea le interfacce EventPublisher e AnalyticsEventWriter.

Non inserire dati sensibili non necessari negli eventi; usa identificativi tecnici e metadati minimi.
```

### 15.5 Sicurezza base

```text
Rivedi il codice di Clinical Signing Hub introducendo requisiti minimi di sicurezza: nessuna password provider salvata, sessione provider temporanea, audit di accessi e anteprime, mascheramento dei dati sensibili nei log, validazione dei filtri di input, controllo delle autorizzazioni per vedere solo i referti associati al firmatario, CSRF/CORS configurati correttamente e separazione tra autenticazione applicativa e autenticazione del provider di firma.
```

### 15.6 Modello dominio e database H-Sign

```text
Stiamo sviluppando H-Sign, un Clinical Signing Hub open source per la firma digitale remota dei referti sanitari. Implementa il modello dominio backend e le migration database.

Entità richieste: Partition, Company, User, Role, Group, SignatureProvider, SignatureAccount, SourceSystem, FseFacilityMapping, Report, PatientMetadata, ClinicalDocument, Hl7Message, SignatureBatch, SignatureAttempt, AuditEvent, AnalyticsEvent.

Il sistema deve supportare:
- utenti amministratori e firmatari;
- partizioni logiche tipo SIADOM/POLICLINICO;
- gruppi di firmatari;
- provider di firma configurabili;
- sistemi eroganti con flag active, cdaType, pdfA3Conversion, visibleSignature, multipleSignature, sendUnsigned, createCda, passthrough;
- mapping di presidi, unità operative e sistemi eroganti per FSE;
- referti con stato, paziente, firmatario, sistema erogante, ID esterno, ID FSE, date e flag tecnici;
- messaggi HL7 input/output processati o scartati;
- batch di firma massiva e tentativi di firma per singolo documento;
- audit append-only.

Usa PostgreSQL. Non salvare password dei provider di firma. Usa UUID come identificatori tecnici. Prevedi indici per codice fiscale firmatario, ID referto, ID esterno, ID FSE, sistema erogante, stato e data modifica.
```

### 15.7 Layout admin

```text
Crea il frontend admin di H-Sign in React/Next.js con TypeScript.

Deve avere una sidebar sinistra con titolo "H-Sign Admin" e sezioni:

Ricerca
- Referti

Monitoraggio
- Messaggi scartati input
- Messaggi scartati output
- Messaggi processati input
- Messaggi processati output
- Referti senza firmatario

Gestione
- Utenti
- Gruppi
- Provider di firma
- Sistemi eroganti
- Presidi eroganti
- Aziende
- Partizioni

Implementa layout, routing e componenti base. Usa dati mock. Lo stile deve essere sobrio, simile a un gestionale sanitario: sidebar chiara, tabelle dense, pulsanti blu per azioni primarie, verde per export/conferma, grigio per annulla e rosso per elimina.
```

### 15.8 Gestione utenti

```text
Implementa la pagina Admin Utenti di H-Sign.

Funzioni:
- ricerca per utente, cognome e codice fiscale;
- pulsanti Nuovo, Esporta, Importa da SAC, Importa da Excel;
- warning se la ricerca produce più risultati di quelli visibili;
- tabella con colonne azione modifica, utente, cognome, nome, codice fiscale, partizione, ruoli, gruppi, email, attivo;
- icona modifica per aprire il dettaglio utente.

Implementa anche la pagina Nuovo/Modifica utente con sezioni:

Dati utente: utente obbligatorio, attivo, cognome, nome, codice fiscale obbligatorio, cellulare, email, descrizione.

Controfirmatario: codice fiscale.

Accessi: checkbox per i ruoli Administrator e Firmatario, tabella dei gruppi associati e pulsante Modifica gruppi.

Footer con Elimina, Conferma, Annulla e Applica.

Usa validazione frontend. Non implementare ancora l'integrazione reale SAC; crea funzioni placeholder.
```

### 15.9 Ricerca referti admin

```text
Implementa la pagina Admin Referti di H-Sign.

Modalità semplice:
- filtri ID referto, ID esterno, periodo modifica e stato;
- regola: i filtri ID referto, ID esterno e ID FSE, quando valorizzati, ignorano tutti gli altri filtri.

Modalità avanzata:
- ID paziente, cognome, nome, tipo referto, firmatario, CF firmatario, data referto, data firma, sistema erogante, multifirma, warning conversione, referto remoto, passthrough, ID FSE, reparto erogante.

Tabella:
- anteprima, paziente, referto, firmatario, stato, data modifica, data referto, data firma, sistema erogante, multifirma, warning conversione, referto remoto.

La cella paziente deve mostrare nome, cognome, sesso, data e luogo di nascita e codice fiscale. La cella referto deve mostrare tipo referto e reparto. La cella firmatario deve mostrare nome, cognome e codice fiscale.

Usa dati mock realistici ma fittizi.
```

### 15.10 Sistemi eroganti

```text
Implementa la pagina Admin Sistemi eroganti di H-Sign.

Filtri: codice, codice azienda, attivo, tipo CDA.
Azioni: Nuovo, Esporta.

Tabella: modifica, codice, codice azienda, tipo CDA, attivo, descrizione, conversione PDF/A3, firma visibile, firma multipla, invia non firmati, crea CDA, passthrough.

Implementa il form Nuovo/Modifica sistema erogante con tutti i campi. I flag devono guidare la futura pipeline documentale:
- createCda: il sistema costruisce CDA2 se non presente;
- passthrough: il sistema accetta un documento già prodotto senza ricostruzione;
- pdfA3Conversion: abilita la conversione PDF/A3;
- visibleSignature: configura la firma visibile;
- multipleSignature: abilita il workflow multifirma;
- sendUnsigned: consente l'invio non firmato solo se esplicitamente configurato.

Aggiungi validazioni: codice obbligatorio, codice azienda obbligatorio e warning per combinazioni incoerenti, per esempio createCda=true e passthrough=true.
```

### 15.11 Presidi eroganti e FSE

```text
Implementa la pagina Admin Presidi eroganti, sottotitolo "Configurazioni per FSE".

Filtri: codice, descrizione, sistema erogante con autocomplete, codice unità operativa.
Azione: Nuovo.
Tabella: modifica, presidio erogante, unità operativa, sistema erogante, reparto.

Implementa il form Nuovo/Modifica mapping FSE con codice presidio, descrizione presidio, codice azienda, codice unità operativa, descrizione unità operativa, sistema erogante, reparto e attivo.

Questo mapping verrà usato per normalizzare i metadati verso FSE 2.0.
```

### 15.12 Monitoraggio flussi

```text
Implementa le pagine di monitoraggio H-Sign:
- Messaggi scartati input;
- Messaggi scartati output;
- Messaggi processati input;
- Messaggi processati output;
- Referti senza firmatario.

Ogni pagina deve avere filtri per periodo, sistema erogante, tipo messaggio, stato, correlationId, ID esterno e codice fiscale paziente quando disponibile.

Le tabelle devono mostrare timestamp, sistema erogante, direzione, tipo messaggio, esito, errore sintetico, correlationId e ID referto se presente.

Il dettaglio messaggio deve mostrare metadati, errore, payload raw mascherato e timeline eventi.

Non mostrare dati sensibili nei log applicativi; nel frontend admin mostrare soltanto ciò che serve al troubleshooting.
```

### 15.13 Backend API admin

```text
Implementa API REST backend per le schermate admin di H-Sign.

Endpoint minimi:

GET /api/admin/users
POST /api/admin/users
GET /api/admin/users/{id}
PUT /api/admin/users/{id}
DELETE /api/admin/users/{id}
POST /api/admin/users/import-sac
POST /api/admin/users/import-excel
GET /api/admin/users/export

GET /api/admin/reports
GET /api/admin/reports/{id}

GET /api/admin/source-systems
POST /api/admin/source-systems
GET /api/admin/source-systems/{id}
PUT /api/admin/source-systems/{id}
DELETE /api/admin/source-systems/{id}
GET /api/admin/source-systems/export

GET /api/admin/fse-facility-mappings
POST /api/admin/fse-facility-mappings
GET /api/admin/fse-facility-mappings/{id}
PUT /api/admin/fse-facility-mappings/{id}
DELETE /api/admin/fse-facility-mappings/{id}

GET /api/admin/monitoring/messages
GET /api/admin/monitoring/messages/{id}
GET /api/admin/monitoring/reports-without-signer

Implementa paginazione, ordinamento, filtri, validazione input e audit degli accessi admin.
```

### 15.14 Pipeline configurabile

```text
Implementa un servizio backend ReportIngestionService per H-Sign.

Dato un messaggio in ingresso simulato, il servizio deve:
1. identificare il SourceSystem;
2. leggere la configurazione del SourceSystem;
3. estrarre metadati di paziente, referto, firmatario, reparto e date;
4. creare Hl7Message con stato processed o discarded;
5. creare o aggiornare Report;
6. se manca il firmatario, mettere Report nello stato MISSING_SIGNER;
7. se mancano dati obbligatori, mettere Report nello stato INCOMPLETE;
8. se createCda=true, chiamare l'interfaccia CdaBuilder;
9. se passthrough=true, saltare CdaBuilder e registrare il documento ricevuto;
10. se pdfA3Conversion=true, chiamare l'interfaccia PdfA3Converter;
11. se tutto è pronto, mettere Report in READY_TO_SIGN;
12. pubblicare AuditEvent e AnalyticsEvent per ogni transizione.

CdaBuilder e PdfA3Converter devono essere interfacce con implementazione mock.
```

### 15.15 Revisione di sicurezza completa

```text
Rivedi H-Sign introducendo requisiti di sicurezza minimi.

Requisiti:
- separare autenticazione applicativa da autenticazione del provider di firma;
- non salvare password provider;
- mascherare codice fiscale e dati paziente nei log tecnici;
- auditare accessi a referti, anteprime, modifiche configurazioni, firme e import utenti;
- impedire a un firmatario di vedere referti non assegnati a lui;
- consentire agli admin di vedere referti secondo partizione e autorizzazioni;
- validare tutti i filtri;
- applicare paginazione obbligatoria;
- bloccare export massivi senza ruolo admin;
- predisporre rate limit sulle API di ricerca;
- rendere configurabile la retention dei raw HL7.
```

## 16. Decisioni architetturali sintetiche

1. H-Sign è un orchestratore modulare, non un firmatore universale monolitico.
2. Il core rimane indipendente dai provider tramite adapter.
3. La pipeline è determinata da `SourceSystem` e dai mapping FSE.
4. PostgreSQL conserva stato e metadati; l'object storage conserva i documenti.
5. Gli eventi sono append-only e costituiscono la base di audit e analytics.
6. Firma massiva significa batch globale più esito indipendente per documento.
7. L'autenticazione applicativa è separata da quella del provider.
8. FSE e conservazione sono integrazioni, non responsabilità monolitiche del core.
9. Il primo MVP privilegia frontend mock, dominio, CRUD configurazioni, ricerca e firma mock.
10. OpenSearch e ClickHouse arrivano dopo la stabilizzazione del modello eventi.

## 17. Fonte e stato del documento

Questo documento riorganizza integralmente le informazioni contenute nel PDF `gestore firma.pdf` in una specifica di progetto consultabile e versionabile nel repository Git.

Il materiale di partenza tratta il mock dell'applicazione firmatario come specifica funzionale iniziale e il mock amministrativo come specifica funzionale **v0.2**. Le scelte tecnologiche e architetturali qui riportate sono proposte progettuali da validare durante l'implementazione e l'integrazione con provider, FSE 2.0 e conservatori effettivamente selezionati.
