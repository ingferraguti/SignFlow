# SignFlow Service Signature Request API

Versione del contratto: `1.0`

Data: 2026-08-24
Specifica machine-readable: [`service-signature-request-openapi.yaml`](service-signature-request-openapi.yaml)

## 1. Scopo e confini

Un sistema chiamante autenticato invia un documento, il tipo documentale, il sottotipo e il firmatario. SignFlow:

1. valida identità tecnica, `SourceSystem`, classificazione e firmatario;
2. conserva l'originale in object storage privato;
3. trasforma il contenuto in PDF/A-3B e accetta la richiesta solo se veraPDF conferma la conformità;
4. restituisce un `requestId` stabile e interrogabile;
5. espone stato firma, disponibilità e download del PDF firmato;
6. espone stato e prova operativa dell'invio in conservazione sostitutiva.

La richiesta generica resta distinta da `Report`/`Referto`: un documento sanitario può essere collegato al flusso
clinico soltanto quando sono disponibili paziente, episodio e metadati FSE; un documento amministrativo non genera
dati clinici fittizi. Le API non includono un provider di firma o conservazione specifico.

`signed=true` indica che EU DSS ha verificato una o più firme PAdES tecnicamente integre e che la revisione originale
coperta dalla firma coincide byte-per-byte con il PDF/A-3B della richiesta. Non equivale, da solo, a una valutazione
giuridica della qualifica del certificato. Il campo `signature.validation` distingue `TRUSTED_VALID` da
`TECHNICALLY_VALID`.

## 2. Endpoint per i sistemi chiamanti

| Metodo | Percorso | Scopo | Ruolo |
| --- | --- | --- | --- |
| `GET` | `/api/integration/signature-requests/document-types` | Catalogo attivo tipo/sottotipo e fonti. | `INGESTION` o `ADMINISTRATOR` |
| `POST` | `/api/integration/signature-requests` | Crea e normalizza una richiesta. | `INGESTION` o `ADMINISTRATOR` |
| `GET` | `/api/integration/signature-requests/{requestId}` | Legge stato firma e conservazione. | `INGESTION` o `ADMINISTRATOR` |
| `GET` | `/api/integration/signature-requests/{requestId}/signed-document` | Scarica il PDF firmato. | `INGESTION` o `ADMINISTRATOR` |

Tutti gli endpoint richiedono `Authorization: Bearer <JWT>`. Le operazioni sul singolo oggetto richiedono
`X-Source-System`; una richiesta non appartenente a quel sistema risponde `404`, evitando la divulgazione di ID validi.
Le risposte contengono `Cache-Control: no-store`.

## 3. Formati sorgente e normalizzazione PDF/A-3

### 3.1 Formati supportati

| Contenuto | Media type dichiarabili | Note |
| --- | --- | --- |
| PDF | `application/pdf`, `application/x-pdf`, `application/acrobat`, `application/vnd.pdf`, `applications/vnd.pdf`, `text/pdf`, `application/octet-stream` | Sono interpretati i PDF non cifrati e non già firmati supportati da PDFBox 3.0.6, indipendentemente da versione PDF 1.x/2.0 o profilo sorgente PDF/A, PDF/X, PDF/E, PDF/UA. |
| PNG | `image/png`, `image/x-png`, `application/octet-stream` | Una pagina. Canale alpha appiattito su bianco. |
| JPEG | `image/jpeg`, `image/jpg`, `image/pjpeg`, `application/octet-stream` | Una pagina. |
| TIFF | `image/tiff`, `image/tif`, `image/x-tiff`, `application/octet-stream` | Anche multipagina quando il reader ImageIO della JVM supporta la codifica. |
| Testo UTF-8 | `text/plain` | Impaginazione A4 rasterizzata; UTF-8 malformato o contenuto binario è rifiutato. |

Con `application/octet-stream` il formato è rilevato dal contenuto. Per gli altri media type, dichiarazione e contenuto
devono coincidere. Estensione e media type non sono mai sufficienti a far accettare un file.

Non sono supportati: PDF cifrati/protetti da password, PDF già firmati, PDF malformati o senza pagine, archivi,
eseguibili, DOC/DOCX, ODT, RTF, XLS/XLSX e formati immagine diversi da quelli elencati. Un PDF firmato viene rifiutato
perché la normalizzazione invaliderebbe la firma preesistente.

### 3.2 Trasformazione

La trasformazione è intenzionalmente conservativa:

- ogni pagina PDF viene renderizzata RGB a 144 DPI; immagini e testo diventano pagine raster;
- trasparenze vengono appiattite su bianco;
- il nuovo PDF usa versione 1.7, XMP `pdfaid:part=3`, `pdfaid:conformance=B` e output intent sRGB;
- il risultato è validato contro il profilo PDF/A-3B con veraPDF 1.30.2;
- se la validazione non è conforme, la richiesta è rifiutata e non viene accodata alla firma.

Questa strategia preserva l'aspetto visivo ma elimina ricerca testuale, livelli, form interattivi, allegati, JavaScript,
annotazioni e struttura di accessibilità. È una conformità tecnica PDF/A-3B verificata, non una certificazione di
processo o un'attestazione normativa del contenuto.

Limiti predefiniti:

- sorgente e PDF/A-3 risultante: `10 MiB` ciascuno (`SIGNFLOW_DOCUMENT_MAX_SIZE_BYTES`);
- pagine: `100` (`SIGNFLOW_SIGNATURE_MAX_PAGES`);
- pixel renderizzati complessivi: `100.000.000` (`SIGNFLOW_SIGNATURE_MAX_RENDERED_PIXELS`);
- DPI: `144` (`SIGNFLOW_SIGNATURE_PDFA3_RENDER_DPI`, intervallo applicato 96-300).

## 4. Creazione della richiesta

`POST /api/integration/signature-requests` usa `multipart/form-data` con due parti obbligatorie:

- `request`: JSON con `Content-Type: application/json`;
- `file`: contenuto sorgente con filename sicuro e media type coerente.

Header:

| Header | Obbligatorio | Regola |
| --- | --- | --- |
| `Authorization` | sì | JWT con ruolo ammesso. |
| `X-Source-System` | sì | Codice di un `SourceSystem` attivo. |
| `X-Idempotency-Key` | raccomandato | 1-160 caratteri. Se assente usa `externalRequestId`. |
| `X-Correlation-ID` | no | `[A-Za-z0-9._:-]{1,160}`; generato se assente. |

JSON della parte `request`:

```json
{
  "externalRequestId": "CALLER-REQUEST-001",
  "documentType": "HEALTHCARE",
  "documentSubtype": "LABORATORY_REPORT",
  "signer": {
    "scheme": "IT_TAX_CODE",
    "issuingCountry": "IT",
    "issuer": "AGENZIA_ENTRATE",
    "value": "<qualified-signer-identifier>"
  }
}
```

Vincoli:

- `externalRequestId`: obbligatorio, massimo 120 caratteri, univoco per `SourceSystem`;
- `documentType`: `HEALTHCARE` oppure `ADMINISTRATIVE`;
- `documentSubtype`: codice attivo appartenente esattamente al tipo indicato;
- `signer.scheme`: `IT_TAX_CODE`, `EIDAS_PERSON_IDENTIFIER` o `NATIONAL_ID`;
- `issuingCountry`: due lettere ISO maiuscole;
- `issuer`: namespace/autorità emittente, massimo 120 caratteri;
- `value`: massimo 240 caratteri; serve alla risoluzione ma non viene copiato in richiesta o audit;
- il firmatario deve risolvere una `NaturalPerson` attiva con almeno un profilo applicativo attivo nel ruolo `SIGNER`.

Esempio:

```bash
curl -X POST "$BASE_URL/api/integration/signature-requests" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Source-System: LIS-DEMO" \
  -H "X-Idempotency-Key: caller-request-001" \
  -H "X-Correlation-ID: caller-correlation-001" \
  -F 'request={"externalRequestId":"CALLER-REQUEST-001","documentType":"HEALTHCARE","documentSubtype":"LABORATORY_REPORT","signer":{"scheme":"IT_TAX_CODE","issuingCountry":"IT","issuer":"AGENZIA_ENTRATE","value":"<qualified-signer-identifier>"}};type=application/json' \
  -F 'file=@document.pdf;type=application/pdf'
```

Prima creazione: `201 Created`, header `Location` e body completo. Retry identico: `200 OK`, stesso `requestId`,
stesso `correlationId` originale e `idempotent=true`. Riutilizzo della chiave con metadati/file diversi: `409 Conflict`.

## 5. Risposta e semantica dei campi

```json
{
  "requestId": "5d01f765-a88f-44b2-b2ad-8f51711ed585",
  "externalRequestId": "CALLER-REQUEST-001",
  "sourceSystemCode": "LIS-DEMO",
  "documentType": "HEALTHCARE",
  "documentSubtype": "LABORATORY_REPORT",
  "documentSubtypeDisplayName": "Referto di laboratorio",
  "status": "SIGNED",
  "signer": {
    "naturalPersonId": "c38cc660-56ea-4cc1-bf85-00a78d699838",
    "displayName": "Firmatario Fittizio"
  },
  "sourceDocument": {
    "originalFilename": "document.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 42011,
    "sha256": "<64 lowercase hex>"
  },
  "normalizedDocument": {
    "documentId": "01fc4769-a197-425d-a0de-47ec54070dda",
    "filename": "document.pdf",
    "contentType": "application/pdf",
    "sizeBytes": 186042,
    "sha256": "<64 lowercase hex>",
    "profile": "PDF/A-3B",
    "pdfaPart": "3",
    "pdfaConformance": "B",
    "validator": "veraPDF 1.30.2",
    "pageCount": 1
  },
  "signature": {
    "signed": true,
    "signedAt": "2026-08-24T08:15:30Z",
    "signatureCount": 1,
    "validation": "TECHNICALLY_VALID",
    "signedDocumentAvailable": true,
    "signedDocumentSha256": "<64 lowercase hex>",
    "downloadPath": "/api/integration/signature-requests/5d01f765-a88f-44b2-b2ad-8f51711ed585/signed-document"
  },
  "conservation": {
    "status": "SENT",
    "sent": true,
    "sentAt": "2026-08-24T08:16:00Z",
    "completedAt": null,
    "remoteReference": "CONSERVATION-REFERENCE-001",
    "errorCode": null
  },
  "correlationId": "caller-correlation-001",
  "submittedAt": "2026-08-24T08:14:00Z",
  "idempotent": false
}
```

Invarianti utili a client e LLM:

- `status == "SIGNED"` se e solo se `signature.signed == true` e `signedDocumentAvailable == true`;
- prima della firma: `signature.signed=false`, campi temporali/hash/path firma `null`;
- `conservation.sent == true` se e solo se esiste `conservation.sentAt` e lo stato è `SENT`, `ACCEPTED`, `REJECTED` o `FAILED` dopo un invio;
- `conservation.completedAt` è valorizzato solo negli stati terminali `ACCEPTED`, `REJECTED`, `FAILED`;
- hash SHA-256 sono sempre 64 caratteri esadecimali minuscoli;
- timestamp sono ISO-8601 con offset;
- non dedurre il tipo dal filename: usare `documentType` e `documentSubtype`;
- non dedurre una firma valida dal solo download: usare `signature.signed` e `signature.validation`.

## 6. Lettura stato e download

```bash
curl "$BASE_URL/api/integration/signature-requests/$REQUEST_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Source-System: LIS-DEMO"
```

Il client può usare polling con backoff (per esempio 2, 5, 10, 30 secondi, poi massimo 60 secondi) e deve fermarsi
quando `status` è `SIGNED`, `REJECTED` o `FAILED`. La conservazione ha un ciclo indipendente e può proseguire dopo
`SIGNED`.

```bash
curl "$BASE_URL/api/integration/signature-requests/$REQUEST_ID/signed-document" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Source-System: LIS-DEMO" \
  --output signed.pdf
```

Il download restituisce `200 application/pdf`, `Content-Disposition: attachment`, `Content-Length`,
`X-Document-SHA256` e `Cache-Control: no-store`. Se la richiesta esiste ma il firmato non è disponibile, restituisce
`409`; SourceSystem errato o ID inesistente restituisce `404`.

## 7. Stati

Stato richiesta:

| Stato | Significato |
| --- | --- |
| `PENDING_SIGNATURE` | PDF/A-3B pronto; firma non ancora acquisita. |
| `SIGNING` | Riservato a un orchestratore che ha avviato il provider. |
| `SIGNED` | PDF PAdES verificato e scaricabile. |
| `REJECTED` | Firma rifiutata. |
| `FAILED` | Errore non recuperato del flusso firma. |

Stato conservazione:

| Stato | `sent` | Terminale | Significato |
| --- | --- | --- | --- |
| `NOT_REQUESTED` | false | no | Nessuna richiesta di conservazione. |
| `PENDING` | false | no | Pianificata/accodata ma non confermata come inviata. |
| `SENT` | true | no | Inviata; `remoteReference` obbligatoria. |
| `ACCEPTED` | true | sì | Accettata dal sistema di conservazione. |
| `REJECTED` | true | sì | Rifiutata dopo l'invio. |
| `FAILED` | dipende da `sentAt` | sì | Fallita; `sent` chiarisce se l'invio era già avvenuto. |

## 8. Catalogo documentale

Il client deve usare `GET .../document-types` come fonte runtime. Le liste seguenti descrivono il seed V24.

### `HEALTHCARE`

`LABORATORY_REPORT`, `RADIOLOGY_REPORT`, `SPECIALIST_OUTPATIENT_REPORT`, `PATHOLOGY_REPORT`,
`EMERGENCY_DEPARTMENT_REPORT`, `DISCHARGE_LETTER`, `PATIENT_SUMMARY`, `PHARMACEUTICAL_PRESCRIPTION`,
`SPECIALIST_PRESCRIPTION`, `CLINICAL_RECORD`, `DRUG_DISPENSATION`, `SINGLE_VACCINATION_RECORD`,
`VACCINATION_CERTIFICATE`, `SPECIALIST_CARE_DELIVERY`, `IMPLANT_CARD`, `PREVENTION_INVITATION`,
`PERSONAL_HEALTH_NOTEBOOK`.

Fonte: [AgendaDigitale, documenti FSE 2.0](https://www.agendadigitale.eu/sanita/fascicolo-sanitario-elettronico-2-0-linnovazione-che-puo-ridisegnare-la-sanita-italiana/).

### `ADMINISTRATIVE`

`CONTRACT`, `RESOLUTION`, `OPINION`, `TECHNICAL_REPORT`, `EXPENSE_REPORT`, `INVOICE`, `DELIVERY_NOTE`,
`PROJECT_DOCUMENT`, `ADMINISTRATIVE_ACT`, `PROTOCOLLED_COMMUNICATION`.

È un catalogo operativo estensibile, non una tassonomia normativa chiusa. Fonti AgendaDigitale:
[gestione documentale PA](https://www.agendadigitale.eu/documenti/intelligenza-artificiale-nella-pa-come-cambia-la-gestione-documentale/),
[note spese](https://www.agendadigitale.eu/documenti/dematerializzazione-delle-note-spese-i-requisiti-e-le-indicazioni-dellagenzia-delle-entrate/),
[documenti da conservare](https://www.agendadigitale.eu/documenti/conservazione-dei-documenti-digitali-i-metodi-e-le-differenze-col-backup/),
[documenti protocollati](https://www.agendadigitale.eu/documenti/gestione-documentale-tutte-le-novita-delle-linee-guida-agid-per-imprese-e-professionisti/).

## 9. Errori

Formato comune:

```json
{
  "timestamp": "2026-08-24T08:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Document cannot be transformed into a valid PDF/A-3B",
  "path": "/api/integration/signature-requests"
}
```

| HTTP | Uso |
| --- | --- |
| `400` | JSON/header/filename/multipart non valido, parte mancante, media type incoerente. |
| `401` | Bearer token assente o non valido. |
| `403` | Ruolo non ammesso. |
| `404` | ID inesistente o fuori dallo scope SourceSystem. |
| `409` | Collisione idempotenza/external ID, firmato non pronto, transizione non ammessa. |
| `413` | Sorgente o PDF/A-3 risultante oltre limite. |
| `415` | Formato contenuto non supportato. |
| `422` | SourceSystem/sottotipo/firmatario non valido, PDF cifrato/firmato, conversione o verifica firma fallita. |
| `500` | Errore interno non classificato. |
| `503` | Object storage non disponibile. |

Il client deve considerare retryable `503` e alcuni `500`; `409` è retryable soltanto dopo una nuova lettura dello
stato. Non ripetere `400`, `413`, `415`, `422` senza modificare la richiesta.

## 10. Endpoint riservati agli adapter

Questi endpoint non sono destinati al sistema chiamante ma completano il contratto provider-neutral.

| Metodo | Percorso | Ruolo | Funzione |
| --- | --- | --- | --- |
| `GET` | `/api/adapters/signature-requests/{id}/signable-document` | `SIGNATURE_ADAPTER` o `ADMINISTRATOR` | Scarica l'esatto PDF/A-3 da firmare. |
| `POST` | `/api/adapters/signature-requests/{id}/signed-document` | `SIGNATURE_ADAPTER` o `ADMINISTRATOR` | Registra multipart `file` PAdES; `X-Idempotency-Key` obbligatorio. |
| `POST` | `/api/adapters/signature-requests/{id}/conservation-status` | `CONSERVATION_ADAPTER` o `ADMINISTRATOR` | Registra uno stato; `X-Idempotency-Key` obbligatorio. |

Il PDF firmato è accettato solo se EU DSS conferma firme PAdES tecnicamente valide e recupera come revisione originale
esattamente il PDF/A-3 scaricato. Per la conservazione sono ammesse le transizioni:

```text
NOT_REQUESTED -> PENDING -> SENT -> ACCEPTED | REJECTED | FAILED
NOT_REQUESTED ------------> SENT -> ACCEPTED | REJECTED | FAILED
PENDING --------------------------> FAILED
```

Esempio body conservazione:

```json
{
  "status": "SENT",
  "remoteReference": "CONSERVATION-REFERENCE-001",
  "errorCode": null,
  "occurredAt": "2026-08-24T08:16:00Z"
}
```

Gli adapter sono autenticati separatamente dall'autenticazione remota del provider. Password, OTP, chiavi private,
payload documentali e identificativi qualificati del firmatario non vengono scritti nei log o negli audit.

## 11. Persistenza, audit e integrità

- Originale, PDF/A-3 e firmato sono oggetti distinti e immutabili in MinIO privato con chiavi opache.
- PostgreSQL conserva metadati, SHA-256 e riferimenti; nessuna colonna `bytea` contiene documenti.
- Una richiesta ha al massimo un artefatto per tipo: `ORIGINAL`, `NORMALIZED_PDFA3`, `SIGNED`.
- Ricezione, firma e stati di conservazione producono eventi append-only e audit globali minimizzati.
- Le chiavi idempotenti e i fingerprint proteggono anche callback concorrenti/ripetuti.
- Nessun endpoint espone direttamente la chiave MinIO.

## 12. Procedura client raccomandata

1. Leggere e memorizzare temporaneamente il catalogo.
2. Generare `externalRequestId`, idempotency key e correlation ID stabili.
3. Inviare una sola richiesta multipart.
4. Persistire il `requestId` restituito.
5. Ripetere in sicurezza lo stesso POST se la risposta è persa.
6. Interrogare `GET /{requestId}` con lo stesso `X-Source-System`.
7. Quando `signature.signed=true`, verificare `signature.validation` e scaricare da `downloadPath`.
8. Confrontare il file con `X-Document-SHA256`/`signedDocumentSha256`.
9. Continuare il polling se serve l'esito conservazione; usare `conservation.sent` per distinguere accodamento da invio.
10. Conservare correlation ID e codici di errore, mai token o dati sanitari nei log applicativi del chiamante.
