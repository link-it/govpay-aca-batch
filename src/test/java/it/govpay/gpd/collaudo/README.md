# Verifica manuale di GPD v3 sul collaudo pagoPA

Programma da lanciare a mano che esegue su GPD del collaudo un catalogo di casi d'uso e registra richieste, risposte e stato risultante. Le registrazioni servono a chiarire le regole non definite dalla specifica, ad alimentare il simulatore dei servizi pagoPA (esterno a questo progetto) e a confrontare esecuzioni successive.

Non fa parte della suite dei test né del jar applicativo. Non usa Spring né il database.

## Esecuzione

```bash
mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp target/test-classes:target/classes:$(cat target/cp.txt) it.govpay.gpd.collaudo.CollaudoGpd [opzioni]
```

| Opzione | Effetto |
|---|---|
| `--elenca` | Elenca i casi con gruppo e descrizione, senza eseguire nulla |
| `--caso=ID[,ID]` | Esegue solo i casi indicati |
| `--gruppo=NOME[,NOME]` | Esegue solo i gruppi indicati |
| (nessuna) | Esegue tutto il catalogo tranne i gruppi `tempo` (attese di diversi minuti, `--gruppo=tempo`) e `orfani` (`--gruppo=orfani`), che vanno richiesti esplicitamente |

## Configurazione

Variabili d'ambiente:

| Variabile | Obbligatoria | Significato |
|---|---|---|
| `GPD_COLLAUDO_BASE_URL` | sì | URL base delle API v3, per esempio `https://api.uat.platform.pagopa.it/gpd/debt-positions-service/v3` |
| `GPD_COLLAUDO_API_KEY` | sì | Subscription key |
| `GPD_COLLAUDO_ENTE` | sì | Codice fiscale dell'ente creditore del collaudo |
| `GPD_COLLAUDO_IBAN` | sì | IBAN di accredito dei trasferimenti |
| `GPD_COLLAUDO_API_KEY_HEADER` | no | Nome dell'header della chiave (default `Ocp-Apim-Subscription-Key`) |
| `GPD_COLLAUDO_SEGREGAZIONE` | no | Codice di segregazione a due cifre per gli IUV (default `01`) |
| `GPD_COLLAUDO_PREFISSO` | no | Prefisso degli IUPD (default `GVPTEST`) |
| `GPD_COLLAUDO_OSSERVAZIONE_INTERVALLO_SECONDI` | no | Intervallo tra due letture di osservazione (default `2`) |
| `GPD_COLLAUDO_OSSERVAZIONE_DURATA_SECONDI` | no | Durata massima di un'osservazione (default `30`) |
| `GPD_COLLAUDO_PAUSA_MILLISECONDI` | no | Pausa prima di ogni chiamata, per rispettare eventuali limiti di frequenza (default `0`) |
| `GPD_COLLAUDO_SCADENZA_SECONDI` | no | Gruppo `tempo`: tra quanti secondi scadono le date di prova (default `90`) |
| `GPD_COLLAUDO_OSSERVAZIONE_LUNGA_SECONDI` | no | Gruppo `tempo`: per quanto tempo dopo l'evento si continua a leggere (default `300`) |
| `GPD_COLLAUDO_USCITA` | no | Cartella delle registrazioni (default `~/gpd-collaudo`) |

Il file [gpd-collaudo.env.example](gpd-collaudo.env.example) è un modello con tutte le variabili: va copiato fuori dal repository (per esempio in `~/gpd-collaudo.env`), compilato, protetto con `chmod 600` e caricato con `source`.

La chiave e le intestazioni sensibili non vengono scritte né nelle registrazioni né nel riepilogo.

## Errori frequenti sul collaudo

| Risposta | Causa |
|---|---|
| 401 "invalid subscription key" | La chiave non è abilitata al prodotto GPD v3 (una chiave ACA non vale su GPD) |
| 400 "Cannot deserialize ... LocalDateTime" | Le date vanno inviate senza fuso orario, per esempio `2027-01-18T10:03:13.686` |
| 400 "cannot be recovered ... max_days_interval= 30" | Nell'elenco l'intervallo tra `due_date_from` e `due_date_to` non può superare 30 giorni |
| 403 "does not have proper authorization to access or modify the IUVs" | Il codice di segregazione degli IUV (`GPD_COLLAUDO_SEGREGAZIONE`) non è quello abilitato per la chiave e l'ente |

## Identificativi

IUPD: prefisso, data e ora, suffisso casuale. IUV: 17 cifre composte da codice di segregazione, progressivo di 13 cifre e resto della divisione per 93. NAV: `3` seguito dall'IUV. Il NAV non è indipendente dall'IUV: le combinazioni provate sono IUPD da solo, coppia NAV/IUV da sola, e IUV con NAV omesso (il NAV è facoltativo nel contratto).

## Esiti

| Esito | Significato |
|---|---|
| `OSSERVATO` | Il caso è stato eseguito e il comportamento è stato registrato (qualunque sia) |
| `NON_ESEGUIBILE` | Un prerequisito non si è verificato: stato iniziale non raggiunto, creazione o cancellazione di preparazione rifiutata. Il motivo è nel campo `motivo` |
| `NON_DETERMINABILE` | Il risultato non si è stabilizzato entro la durata massima di osservazione |
| `ERRORE_TECNICO` | Errore del programma o di trasporto che ha impedito la verifica |

Un timeout di rete durante una scrittura non viene trattato come scrittura non avvenuta: il programma fa una lettura di verifica (non ripete la scrittura), la registra e inserisce la posizione tra quelle con esito incerto. Le scritture non vengono mai ripetute automaticamente.

## Fasi di ogni caso

Ogni passo registrato porta la sua fase:

- `preparazione`: creazione delle posizioni nello stato richiesto, verificato con `GET` (con osservazione fino a intervallo e durata configurati);
- `prova`: operazioni oggetto del caso;
- `verifica`: letture successive alle scritture;
- `pulizia`: cancellazione delle posizioni create dal caso e ancora presenti.

Ogni caso prepara le proprie posizioni: una ricreazione riuscita in un caso non altera gli altri.

## Catalogo

Versione `4`.

### Gruppo `cancellazione`

| Caso | Prerequisito | Passi | Osservazioni |
|---|---|---|---|
| `CANC_01_DRAFT` | Posizione in `DRAFT` verificata | `DELETE`; letture fino a 404; `DELETE` ripetuto | Esito del `DELETE`; momento in cui la lettura dà 404, o stato rimasto; esito del `DELETE` ripetuto |
| `CANC_02_PUBLISHED` | Posizione in `PUBLISHED` (`validityDate` futura) verificata | come sopra | come sopra |
| `CANC_03_VALID` | Posizione in `VALID` verificata | come sopra | come sopra |
| `RIUSO_01_STESSO_IUPD_STESSA_COPPIA` | Posizione `VALID` cancellata, cancellazione verificata | `POST` con stesso IUPD e stessi NAV e IUV; letture | Se IUPD e coppia NAV/IUV cancellati sono riusabili |
| `RIUSO_02_STESSO_IUPD_COPPIA_NUOVA` | come sopra | `POST` con stesso IUPD e NAV/IUV nuovi | Se l'IUPD cancellato è riusabile |
| `RIUSO_03_IUPD_NUOVO_STESSA_COPPIA` | come sopra | `POST` con IUPD nuovo e stessi NAV e IUV | Se NAV e IUV cancellati tornano liberi |
| `RIUSO_04_IUPD_NUOVO_STESSO_IUV_NAV_OMESSO` | come sopra | `POST` con IUPD nuovo, stesso IUV e `nav` omesso | Se il conflitto dipende dall'IUV |
| `ELENCO_01_POSIZIONE_CANCELLATA` | Posizione `VALID` cancellata e una seconda posizione `VALID` di controllo | Elenco senza filtro e con stato `VALID`, finestra di `dueDate` di 20 giorni | Se la posizione cancellata compare; la posizione di controllo deve comparire, altrimenti l'elenco non è significativo |

### Gruppo `duplicati`

Prerequisito comune: posizione `VALID` verificata, ancora presente.

| Caso | Passi | Osservazioni |
|---|---|---|
| `DUP_01_STESSO_IUPD_COPPIA_NUOVA` | `POST` con stesso IUPD e NAV/IUV nuovi; lettura dell'originale | Codice e corpo dell'errore sull'IUPD da solo |
| `DUP_02_IUPD_NUOVO_STESSA_COPPIA` | `POST` con IUPD nuovo e stessi NAV e IUV | Codice e corpo dell'errore sulla coppia NAV/IUV |
| `DUP_03_IUPD_NUOVO_STESSO_IUV_NAV_OMESSO` | `POST` con IUPD nuovo, stesso IUV, `nav` omesso | Se il conflitto dipende dall'IUV |
| `DUP_04_STESSO_IUPD_STESSA_COPPIA` | `POST` con stesso IUPD e stessi NAV e IUV | Codice e corpo dell'errore sul duplicato completo |

### Gruppo `put`

Il PUT invia sempre il corpo completo della posizione. Ogni caso registra, prima e dopo, le opzioni lette con `GET` (debitore, IUV e importo di ogni rata). Le posizioni con due opzioni usano IUV distinti; i casi a due debitori usano due codici fiscali di prova diversi.

| Caso | Prerequisito | Passi | Osservazioni |
|---|---|---|---|
| `PUT_01_OPZIONE_AGGIUNTA` | `VALID`, un'opzione A | `PUT` con A e una nuova B | Se B compare (aggiunta consentita) |
| `PUT_02_OPZIONE_OMESSA` | `VALID`, due opzioni A e B dello stesso debitore | `PUT` con la sola A | Se B resta (unione) o sparisce (sostituzione) |
| `PUT_03_OPZIONE_OMESSA_RIUSO_IUV` | come sopra | `PUT` con la sola A; `POST` di una nuova posizione con IUV e NAV di B | Se IUV e NAV dell'opzione omessa tornano liberi |
| `PUT_04_DUE_DEBITORI_OMISSIONE_UNO` | `VALID`, un'opzione per ciascuno di due debitori | `PUT` con la sola opzione del secondo | Se l'opzione del primo debitore viene rimossa |
| `PUT_05_DRAFT_A_PUBBLICATA` | `DRAFT` | `PUT` con `toPublish=true` | Stato risultante |
| `PUT_06_PUBLISHED_A_DRAFT` | `PUBLISHED` | `PUT` con `toPublish=false` | Se si torna a `DRAFT` |
| `PUT_07_VALID_A_DRAFT` | `VALID` | `PUT` con `toPublish=false` | Se si torna a `DRAFT` |
| `PUT_08_VALID_IMPORTO_MODIFICATO` | `VALID` | `PUT` con importo diverso | Se l'importo cambia |
| `PUT_09_VALID_IUV_MODIFICATO` | `VALID` | `PUT` con IUV e NAV diversi; `POST` di una nuova posizione con quelli vecchi | Se l'IUV è modificabile e quello sostituito torna libero |
| `PUT_10_RIPETIZIONE_IDENTICA` | `VALID` | stesso `PUT` due volte | Se la ripetizione è accettata |
| `PUT_11_IUPD_INESISTENTE` | nessuno | `PUT` su un IUPD mai creato | Se risponde 404 o crea la posizione |

### Gruppo `date`

Ogni caso registra, dopo la scrittura, stato e date lette con `GET` (`validityDate`, `dueDate`, `switchToExpired`).

| Caso | Passi | Osservazioni |
|---|---|---|
| `DATE_01_VALIDITY_FUTURA` | `POST` pubblicato con `validityDate` tra 30 giorni | Stato risultante |
| `DATE_02_VALIDITY_PASSATA` | `POST` pubblicato con `validityDate` di ieri | Accettata o rifiutata; stato |
| `DATE_03_DUEDATE_PASSATA_CREAZIONE` | `POST` pubblicato con `dueDate` di cinque giorni fa | Accettata o rifiutata; stato |
| `DATE_04_DUEDATE_PASSATA_AGGIORNAMENTO` | Posizione `VALID`; `PUT` con `dueDate` passata | Accettata o rifiutata; stato |
| `DATE_05_SWITCH_TO_EXPIRED` | `POST` pubblicato con `switchToExpired=true` e `dueDate` futura | Stato risultante |
| `DATE_06_SWITCH_TO_EXPIRED_DUEDATE_PASSATA` | `POST` pubblicato con `switchToExpired=true` e `dueDate` passata | Stato risultante |
| `DATE_07_VALIDITY_SPOSTATA_AVANTI` | Posizione `VALID`; `PUT` con `validityDate` tra 30 giorni | Se la posizione torna a `PUBLISHED` (opzione non pagabile) |
| `DATE_08_VALIDITY_SPOSTATA_INDIETRO` | Posizione `PUBLISHED`; `PUT` senza `validityDate` | Se la posizione diventa `VALID` |
| `DATE_09_OPZIONI_VALIDITY_MISTE` | `POST` pubblicato con due opzioni, una subito valida e una con `validityDate` tra 30 giorni | Stato della posizione con opzioni in stati diversi |
| `DATE_10_DRAFT_DUEDATE_PASSATA` | `POST` in `DRAFT` con `dueDate` passata | Se il controllo sulle date vale anche per le bozze |
| `DATE_11_DRAFT_VALIDITY_PASSATA` | `POST` in `DRAFT` con `validityDate` passata | come sopra |
| `DATE_12_PUT_DRAFT_DUEDATE_PASSATA` | Posizione `DRAFT`; `PUT` con `toPublish=false` e `dueDate` passata | come sopra, in aggiornamento |

### Gruppo `validazione`

Ogni caso crea una posizione pubblicata con un solo difetto rispetto a una posizione valida. Se GPD la accetta, il caso registra la lettura successiva; la pulizia la cancella.

| Caso | Difetto introdotto |
|---|---|
| `VAL_01_RATA_SENZA_IUV` | `iuv` assente |
| `VAL_02_NAV_NON_COERENTE_CON_IUV` | `nav` diverso da `3` seguito dall'IUV |
| `VAL_03_DUEDATE_PRIMA_DI_VALIDITY` | `dueDate` precedente alla `validityDate` |
| `VAL_04_CINQUE_TRASFERIMENTI` | cinque trasferimenti (limite del contratto) |
| `VAL_05_SEI_TRASFERIMENTI` | sei trasferimenti (oltre il limite) |
| `VAL_06_DESCRIZIONE_LUNGA` | descrizione della rata di 600 caratteri |
| `VAL_07_DEBITORE_MANCANTE` | opzione senza `debtor` |
| `VAL_08_OPZIONE_SENZA_RATE` | elenco `installments` vuoto |
| `VAL_09_IMPORTO_TRASFERIMENTI_DIVERSO` | importo del trasferimento diverso da quello della rata |
| `VAL_10_IMPORTO_ZERO` | rata e trasferimento con importo 0 |
| `VAL_11_DUE_OPZIONI_STESSO_IUV` | due opzioni della stessa posizione con lo stesso IUV |
| `VAL_12_NAV_OMESSO` | `nav` omesso (si osserva se GPD lo assegna) |
| `VAL_13_IUPD_TROPPO_LUNGO` | IUPD di circa 100 caratteri |
| `VAL_15_IUV_NON_NUMERICO` | IUV non numerico |
| `VAL_16_IUV_CONTROLLO_ERRATO` | cifre di controllo dell'IUV sbagliate |
| `VAL_17_IUV_DICIOTTO_CIFRE` | IUV di 18 cifre |
| `VAL_18_IBAN_NON_VALIDO` | IBAN non valido |
| `VAL_19_CODICE_FISCALE_DEBITORE_NON_VALIDO` | codice fiscale del debitore non valido |
| `VAL_20_COMPANY_NAME_MANCANTE` | `companyName` assente |
| `VAL_21_CATEGORY_NON_VALIDA` | `category` del trasferimento non valida |
| `VAL_22_IDTRANSFER_DUPLICATO` | due trasferimenti con lo stesso `idTransfer` |
| `VAL_23_TRASFERIMENTO_ALTRO_ENTE` | secondo trasferimento verso un altro ente beneficiario |
| `VAL_24_TIPO_DEBITORE_MANCANTE` | debitore senza `type` |
| `VAL_25_EMAIL_DEBITORE_NON_VALIDA` | email del debitore non valida |
| `VAL_26_CAUSALE_TRASFERIMENTO_MANCANTE` | `remittanceInformation` assente |

### Gruppo `rate`

| Caso | Passi | Osservazioni |
|---|---|---|
| `RATE_01_DUE_RATE` | `POST` pubblicato con un'opzione di due rate (scadenze a 30 e 60 giorni) | Stato e rate lette |
| `RATE_02_RATA_OMESSA_CON_PUT` | Opzione `VALID` di due rate; `PUT` con la sola prima rata; `POST` con IUV e NAV della seconda | Se la rata omessa sparisce e il suo IUV torna libero |
| `RATE_03_STESSO_IUV_TRA_RATE` | `POST` con due rate dello stesso IUV | Errore o accettazione |
| `RATE_04_DUEDATE_NON_CRESCENTI` | `POST` con la seconda rata che scade prima della prima | Errore o accettazione |

### Gruppo `tempo`

Casi con attese reali, non inclusi nell'esecuzione predefinita (`--gruppo=tempo`). Le date di prova sono calcolate in UTC, il fuso con cui GPD sembra confrontare le date senza fuso. Ogni caso crea le posizioni, aspetta `GPD_COLLAUDO_SCADENZA_SECONDI`, poi legge ogni 15 secondi per `GPD_COLLAUDO_OSSERVAZIONE_LUNGA_SECONDI` e registra se e dopo quanto lo stato cambia. Con i valori predefiniti ogni caso dura fino a circa 7 minuti. Se si interrompe il programma durante un caso, le posizioni di prova (prefisso `GVPTEST`) restano sul collaudo.

| Caso | Posizioni | Osservazioni |
|---|---|---|
| `TEMPO_01_VALIDITY_A_DATA` | Una posizione con `validityDate` tra pochi secondi (`PUBLISHED`); una con due opzioni, una subito valida e una differita | Se e dopo quanto passano a `VALID`; stato della posizione con opzioni miste |
| `TEMPO_02_SCADENZA_A_DATA` | Tre posizioni `VALID` con `dueDate` tra pochi secondi: senza `switchToExpired`, con `switchToExpired`, e con due opzioni di cui una sola scade | Se e dopo quanto lo stato cambia; poi `PUT` identico dopo la scadenza, `PUT` con nuova `dueDate`, `PUT` che omette l'opzione scaduta |

Non coperti: pagamenti e propagazione sul nodo.

## Registrazioni

Ogni esecuzione crea `<uscita>/<data-ora>/` con un file JSON per caso e `riepilogo.json`.

Il file di un caso contiene: identificativo e versione del caso, gruppo, descrizione, istanti di avvio e fine con fuso orario, nomi logici delle posizioni con il loro IUPD, NAV e IUV reali, stato iniziale osservato, note, esito con motivo e l'elenco dei passi. Ogni passo riporta fase, etichetta, istante, metodo, URL, richiesta, stato, intestazioni (senza quelle sensibili), corpo della risposta (anche non JSON), durata ed eventuale errore di trasporto.

Il riepilogo contiene: identificativo dell'esecuzione, versione del catalogo, revisione del codice (`n/d` se non disponibile, con indicazione di modifiche locali non committate), istanti, configurazione non sensibile, conteggio degli esiti, esito di ogni caso, corrispondenza tra nomi logici e identificativi reali, posizioni eliminate, residue e con esito incerto.

Le registrazioni conservano i valori originali; i nomi logici permettono di confrontare esecuzioni che usano identificativi e date diversi.

## Pulizia

Il programma cancella soltanto posizioni create nella stessa esecuzione e rifiuta qualunque `DELETE` su un altro IUPD. Al termine di ogni caso cancella le posizioni del caso ancora presenti; la pulizia è registrata nella fase `pulizia`, separata dalle cancellazioni previste dai casi. Il riepilogo elenca le posizioni eliminate, quelle residue e quelle con esito incerto.

La cancellazione non garantisce il ripristino completo del collaudo: possono restare vincoli sugli identificativi usati.

### Gruppo `orfani`

Casi che lasciano sul collaudo posizioni che l'API non permette piu' di leggere, aggiornare o eliminare; non sono inclusi nell'esecuzione predefinita e ogni esecuzione ne lascia una nuova.

| Caso | Cosa si osserva |
|---|---|
| `ORFANI_01_IUPD_CARATTERI_SPECIALI` | IUPD con `$`: la creazione e' accettata, ma lettura, PUT e DELETE falliscono perche' il path accetta solo `[\w*\h-]+` |

Il gruppo `tempo` comprende anche due casi sul ritiro dopo la scadenza (versione 6 del catalogo):

| Caso | Cosa si osserva |
|---|---|
| `TEMPO_03_RITIRO_DOPO_SCADENZA` | PUT con `toPublish=false` su posizioni con opzioni scadute (con e senza `switchToExpired`, con una sola opzione e con due), con e senza l'opzione scaduta, poi ripubblicazione |
| `TEMPO_04_RATA_SCADUTA_IN_PIANO` | Piano di due rate di cui la prima scade: PUT con tutte le rate e con la sola rata futura, con `toPublish` vero e falso |
