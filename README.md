<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - ACA Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-aca-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-aca-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-aca-batch?label=Docker%20Hub&logo=docker)](https://hub.docker.com/r/linkitaly/govpay-aca-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-aca-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Flink-it%2Fgovpay-bom%2Fmain%2Fpom.xml&query=%2F%2F*%5Blocal-name()%3D'spring-boot.version'%5D&label=Spring%20Boot&color=brightgreen)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

Batch Spring Boot che alimenta l'Archivio Centralizzato Avvisi (ACA) di pagoPA: carica, aggiorna e annulla sul servizio GPD (Gestione Posizioni Debitorie) le pendenze gestite da GovPay.

## Sommario

Il batch esegue un job Spring Batch, `pendenzaSenderJob`, sul database di GovPay:

| Job | Default | Cosa fa |
|-----|---------|---------|
| `pendenzaSenderJob` | attivo, ogni 10 minuti | Individua le pendenze `NON_ESEGUITO` o `ANNULLATO` modificate negli ultimi `it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni` giorni e non ancora comunicate all'ACA dopo l'ultima modifica, e le invia al GPD: crea e pubblica le posizioni nuove, aggiorna quelle già inviate, riporta in `DRAFT` quelle annullate |

L'endpoint del GPD e le credenziali si ricavano dal connettore ACA dell'intermediario del dominio, configurato in GovPay (campo `cod_connettore_aca` della tabella `intermediari`, connettore nella tabella `connettori`). Le chiamate al GPD sono registrate nel Giornale degli Eventi.

Il job si avvia da uno scheduler interno (modalità di default), una sola volta con il profilo `cron`, oppure a richiesta con l'API REST descritta in [Avvio manuale via REST](#avvio-manuale-via-rest). Le esecuzioni sono coordinate tra più nodi tramite i metadati Spring Batch sul database.

Storico delle modifiche nel [ChangeLog](ChangeLog).

## Integrazione con GovPay

| Componente | Quando serve | Obbligatorio | Ruolo |
|------------|--------------|--------------|-------|
| Database di GovPay | runtime | sì | Legge le pendenze dalle viste `v_versamenti_gpd` e `v_sng_versamenti_gpd` (su `versamenti`, `singoli_versamenti`, `domini`, `applicazioni`, `uo`, `tributi`, `tipi_tributo`, `iban_accredito`), oltre a `intermediari`, `stazioni`, `connettori` e `configurazione`; aggiorna `versamenti.data_ultima_comunicazione_aca`. Le tabelle sono create dagli script di installazione di GovPay, il batch non crea lo schema |
| Viste del batch | runtime | sì | `v_versamenti_gpd` e `v_sng_versamenti_gpd`, create dallo script `sql/<database>/create-db.sql` di `sql.zip` (vedi [Schema del database](#schema-del-database)) |
| Tabelle Spring Batch | runtime | sì | Metadati delle esecuzioni (`batch_job_*`, `batch_step_*`), usati anche per evitare esecuzioni concorrenti tra nodi. Lo schema è in `sql.zip` e l'immagine Docker può crearlo all'avvio |
| [govpay-bom](https://github.com/link-it/govpay-bom) | build | sì | Parent Maven: versioni delle dipendenze e dei plugin, profilo `dist` |
| [govpay-common](https://github.com/link-it/govpay-common) | build | sì | Libreria comune: connettori, entità di anagrafica, coordinamento dei job, API REST del batch, client GDE, transaction id e livelli di log da database |
| Servizio GPD di pagoPA | runtime | sì | API `debt-positions` del GPD (client generato da `src/main/resources/gpd.json`). URL e autenticazione sono nel connettore ACA dell'intermediario |
| GovPay (monolite) | runtime | sì | Crea e modifica le pendenze e aggiorna `versamenti.data_ultima_modifica_aca`, da cui il batch ricava le pendenze da inviare |
| [govpay-gde-api](https://github.com/link-it/govpay-gde-api) | runtime | no | Riceve gli eventi delle chiamate al GPD (`POST /eventi`), se il connettore `govpay_gde_api` è abilitato in GovPay e il giornale degli eventi prevede la registrazione. Un GDE non raggiungibile non blocca il batch |
| [govpay-console-api](https://github.com/link-it/govpay-console-api) | runtime | no | Può avviare il batch e consultarne le esecuzioni tramite l'API REST, se il batch è nel catalogo delle operazioni della console |

Le modifiche ai livelli di log fatte dalla console (riga `log_level` della tabella `configurazione`) raggiungono il batch entro l'intervallo di refresh, senza riavvio.

## Compilazione

Requisiti: JDK 21 e Maven 3.8.1 o successivo.

```bash
mvn clean install -P jar     # fat jar, per esecuzione standalone e container
mvn clean install -P war     # war, per il deploy su application server
```

Il profilo sceglie il packaging (di default `jar`); il risultato è `target/govpay-aca-batch.jar` o `target/govpay-aca-batch.war`. La build esegue i test e l'analisi OWASP Dependency-Check delle dipendenze; per escludere l'analisi OWASP, per esempio in sviluppo:

```bash
mvn clean install -P jar -Dowasp.phase=none
```

Per produrre anche `target/sql.zip`, con lo schema Spring Batch per ogni database, le viste del batch e gli script di svecchiamento dei metadati, usato dall'immagine Docker e allegato alle release:

```bash
mvn clean install -Pjar,dist
```

## Configurazione

Le proprietà si impostano in `application.properties`, da riga di comando (`-D<proprietà>=<valore>` o `--<proprietà>=<valore>`) o con le variabili d'ambiente equivalenti di Spring Boot, indicate nelle tabelle. Con l'immagine Docker si usano anche le variabili descritte in [Esecuzione con Docker](#esecuzione-con-docker).

### Elaborazione

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni` | `IT_GOVPAY_GPD_BATCH_DBREADER_SOGLIATEMPORALERICERCAPENDENZE_NUMEROGIORNI` | `7` | Giorni, dalla data di ultima modifica, entro cui una pendenza viene considerata |
| `it.govpay.gpd.batch.jobs.gpdSenderJob.steps.spedizionePendenzaStep.chunk-size` | `IT_GOVPAY_GPD_BATCH_JOBS_GPDSENDERJOB_STEPS_SPEDIZIONEPENDENZASTEP_CHUNK_SIZE` | `10` | Pendenze elaborate per transazione e fetch size della lettura |
| `it.govpay.gpd.batch.policy.reinvio.403.enabled` | `IT_GOVPAY_GPD_BATCH_POLICY_REINVIO_403_ENABLED` | `false` | Con `true` una pendenza rifiutata dal GPD con `403` (Ente non abilitato) viene segnata come comunicata e non ritentata; con `false` resta da inviare e viene ripresa all'esecuzione successiva |
| `it.govpay.gpd.standIn.enabled` | `IT_GOVPAY_GPD_STANDIN_ENABLED` | `true` | Valore del campo `payStandIn` delle posizioni inviate al GPD (posizione pagabile in modalità stand-in) |
| `it.govpay.gpd.time-zone` | `IT_GOVPAY_GPD_TIME_ZONE` | `Europe/Rome` | Fuso orario con cui si registra `data_ultima_comunicazione_aca` |
| `scheduler.gpdSenderJob.fixedDelayString` | `SCHEDULER_GPDSENDERJOB_FIXEDDELAYSTRING` | `600000` | Intervallo tra la fine di un'esecuzione e l'avvio della successiva, in ms (10 minuti); nel container si imposta in minuti con `GOVPAY_ACA_BATCH_INTERVALLO_CRON` |
| `scheduler.initialDelayString` | `SCHEDULER_INITIALDELAYSTRING` | `1` | Ritardo della prima esecuzione dopo l'avvio, in ms |

### Esecuzione e cluster

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `it.govpay.gpd.batch.clusterId` | `IT_GOVPAY_GPD_BATCH_CLUSTERID` | `GovPay-ACA-Batch` | Identificativo del nodo, registrato nelle esecuzioni e negli eventi del GDE; nel container è l'indirizzo IP |
| `govpay.batch.stale-threshold-minutes` | `GOVPAY_BATCH_STALETHRESHOLDMINUTES` | `120` | Minuti senza aggiornamenti dopo cui un'esecuzione in corso è considerata bloccata e può essere superata |
| `govpay.batch.job-repository.jdbc.enabled` | `GOVPAY_BATCH_JOBREPOSITORY_JDBC_ENABLED` | `true` | Metadati Spring Batch su database (da `govpay-common`); senza, il coordinamento tra nodi non funziona |
| `spring.batch.job.enabled` | `SPRING_BATCH_JOB_ENABLED` | `false` | Avvio automatico dei job da parte di Spring Boot: resta `false`, il job è avviato dallo scheduler o dal profilo `cron` |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `default` | `cron` per eseguire il job una volta e terminare (vedi [Esecuzione](#esecuzione)) |
| `spring.main.web-application-type` | `SPRING_MAIN_WEB_APPLICATION_TYPE` | `servlet` | `none` per non avviare il server HTTP, utile con il profilo `cron`; nel container lo imposta l'entrypoint in esecuzione singola |
| `server.port` | `SERVER_PORT` | `8080` (`10001` nel container) | Porta HTTP dell'API REST del batch e degli endpoint actuator |
| `spring.main.banner-mode` | `SPRING_MAIN_BANNERMODE` | `off` | Banner di Spring Boot all'avvio |
| `spring.jackson.default-property-inclusion` | `SPRING_JACKSON_DEFAULTPROPERTYINCLUSION` | `non_null` | Esclude i campi nulli dal JSON |
| `spring.jackson.time-zone` | `SPRING_JACKSON_TIMEZONE` | `Europe/Rome` | Fuso orario di default della JVM e dell'applicazione |
| `spring.mvc.date-format` | `SPRING_MVC_DATEFORMAT` | `yyyy-MM-dd` | Formato delle date nei parametri delle richieste REST |
| `spring.mvc.datetime-format` | `SPRING_MVC_DATETIMEFORMAT` | `yyyy-MM-dd'T'HH:mm:ssZZZ` | Formato di data e ora nei parametri delle richieste REST |

### Connettori e client HTTP

URL, autenticazione e versione delle API del GPD e del GDE non sono proprietà del batch: sono nei connettori di GovPay (tabella `connettori`), indicati dal campo `cod_connettore_aca` dell'intermediario e, per il GDE, dal connettore `govpay_gde_api`.

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `govpay.client.cache.enabled` | `GOVPAY_CLIENT_CACHE_ENABLED` | `false` | Carica i connettori all'avvio e li tiene in cache; la cache si svuota con `GET /api/batch/cache/clear` |
| `govpay.client.async.core-pool-size` | `GOVPAY_CLIENT_ASYNC_COREPOOLSIZE` | `10` | Thread minimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.max-pool-size` | `GOVPAY_CLIENT_ASYNC_MAXPOOLSIZE` | `50` | Thread massimi per l'invio asincrono degli eventi al GDE |
| `govpay.client.async.queue-capacity` | `GOVPAY_CLIENT_ASYNC_QUEUECAPACITY` | `100` | Eventi in coda in attesa di invio |
| `govpay.client.async.thread-name-prefix` | `GOVPAY_CLIENT_ASYNC_THREADNAMEPREFIX` | `async-http-` | Prefisso dei thread di invio |

### Database

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | - | URL JDBC del database di GovPay (obbligatoria, in alternativa a `spring.datasource.jndi-name`) |
| `spring.datasource.jndi-name` | `SPRING_DATASOURCE_JNDINAME` | - | Nome JNDI del datasource, per il deploy del war su application server |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | - | Utente del database |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | - | Password del database |
| `spring.datasource.driver-class-name` | `SPRING_DATASOURCE_DRIVERCLASSNAME` | dedotto dall'URL | Classe del driver JDBC |
| `spring.jpa.database-platform` | `SPRING_JPA_DATABASEPLATFORM` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.properties.hibernate.dialect` | `SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT` | dedotto dal driver | Dialetto Hibernate |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | - | `Europe/Rome` | Fuso orario con cui Hibernate legge e scrive le date |
| `spring.jpa.hibernate.ddl-auto` | `SPRING_JPA_HIBERNATE_DDLAUTO` | `none` (`create-drop` con database embedded) | Gestione dello schema: lasciare `none` o `validate`, lo schema è di GovPay |
| `spring.jpa.open-in-view` | `SPRING_JPA_OPENINVIEW` | `false` | Sessione JPA limitata al servizio, non estesa alla vista |
| `spring.datasource.hikari.minimum-idle` | `SPRING_DATASOURCE_HIKARI_MINIMUMIDLE` | `10` (`2` nel container) | Connessioni minime inattive del pool |
| `spring.datasource.hikari.maximum-pool-size` | `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` | `10` | Connessioni massime del pool |
| `spring.datasource.hikari.connection-timeout` | `SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT` | `30000` (`20000` nel container) | Attesa massima di una connessione, in ms |
| `spring.datasource.hikari.idle-timeout` | `SPRING_DATASOURCE_HIKARI_IDLETIMEOUT` | `600000` (`10000` nel container) | Tempo dopo cui una connessione inattiva viene chiusa, in ms |
| `spring.datasource.hikari.max-lifetime` | `SPRING_DATASOURCE_HIKARI_MAXLIFETIME` | `1800000` (`600000` nel container) | Durata massima di una connessione, in ms |

I driver JDBC non sono inclusi nel jar: vedi [Esecuzione](#esecuzione).

### Logging

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `logging.file.name` | `LOGGING_FILE_NAME` | - (solo console) | File di log; nel container `/var/log/govpay-aca/govpay-aca.log` |
| `logging.level.it.govpay` | `LOGGING_LEVEL_IT_GOVPAY` | `INFO` | Livello di log dei package GovPay |
| `logging.pattern.level` | `LOGGING_PATTERN_LEVEL` | `%5p [%X{transactionId:-},%X{correlationId:-}]` | Aggiunge a ogni riga transaction id e correlation id dell'esecuzione: il correlation id è quello della richiesta di avvio manuale, oppure generato per gli avvii schedulati |
| `govpay.logging.livelli-dinamici.enabled` | `GOVPAY_LOGGING_LIVELLIDINAMICI_ENABLED` | `true` | Livelli di log letti dalla riga `log_level` della tabella `configurazione` |
| `govpay.logging.livelli-dinamici.intervallo-refresh` | `LOG_LEVEL_REFRESH_INTERVAL` | `60s` | Intervallo di rilettura dei livelli dal database; `0` li legge solo all'avvio |
| `govpay.logging.livelli-dinamici.logger-gestiti[0]` | - | `it.govpay` | Prefissi dei logger modificabili da database |

### Monitoraggio

| Proprietà | Variabile d'ambiente | Default | Descrizione |
|-----------|----------------------|---------|-------------|
| `management.server.port` | `MANAGEMENT_SERVER_PORT` | porta del servizio | Porta dedicata agli endpoint actuator |
| `management.endpoints.web.base-path` | `MANAGEMENT_ENDPOINTS_WEB_BASEPATH` | `/actuator` | Base path degli endpoint actuator |
| `management.endpoints.web.exposure.include` | `MANAGEMENT_ENDPOINTS` | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `management.endpoint.health.show-details` | `MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS` | `when_authorized` | Dettagli dell'health: il servizio non ha autenticazione, quindi restituisce solo lo stato |
| `management.metrics.tags.application` | `MANAGEMENT_METRICS_TAGS_APPLICATION` | `govpay-aca-batch` | Tag che identifica il servizio nelle metriche |

## Esecuzione

### Driver JDBC

I driver JDBC **non sono inclusi** nel fat jar e vanno forniti a runtime. Creare una directory (es. `jdbc-drivers/`) e copiarvi il driver del database utilizzato (versioni gestite da `govpay-bom`):

| Database   | Driver |
|------------|--------|
| PostgreSQL | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) |
| MySQL / MariaDB | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) |
| Oracle     | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) |
| SQL Server | [mssql-jdbc-13.6.1.jre11.jar](https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/13.6.1.jre11/mssql-jdbc-13.6.1.jre11.jar) |
| H2         | [h2-2.5.252.jar](https://repo1.maven.org/maven2/com/h2database/h2/2.5.252/h2-2.5.252.jar) |

### Schema del database

Prima del primo avvio, nel database di GovPay devono esistere:

- le tabelle dei metadati Spring Batch, create con `sql/spring-batch/schema-<database>.sql` di `sql.zip` (possono esistere già se nello stesso database girano altri batch GovPay);
- le viste del batch, create con `sql/<database>/create-db.sql` (`postgresql`, `mysql`, `oracle`, `sqlserver`, `hsql`).

L'immagine Docker crea entrambe da sola se `GOVPAY_ACA_POP_DB_SKIP=FALSE`. Gli script in `sql/cleanup/` cancellano i metadati delle esecuzioni vecchie e vanno pianificati periodicamente.

### Jar standalone

Il jar usa `PropertiesLauncher`: la proprietà `loader.path` indica la directory dei driver JDBC. Configurazione minima per PostgreSQL, con lo scheduler interno:

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-aca-batch.jar
```

Modalità di esecuzione:

| Modalità | Come si attiva | Comportamento |
|----------|----------------|---------------|
| Scheduler interno | default (nessun profilo) | Il processo resta attivo, esegue il job ogni `scheduler.gpdSenderJob.fixedDelayString` ms ed espone API REST e actuator |
| Esecuzione singola | `--spring.profiles.active=cron` | Esegue il job una volta e termina: la pianificazione è affidata a un cron esterno. L'exit code è `0` anche se il job fallisce o è già in esecuzione su un altro nodo, `1` solo se non si riesce a superare un'esecuzione bloccata: l'esito va letto nei log |

```bash
java -Dloader.path=./jdbc-drivers \
     -Dspring.datasource.url=jdbc:postgresql://localhost:5432/govpay \
     -Dspring.datasource.username=govpay \
     -Dspring.datasource.password=<password> \
     -jar target/govpay-aca-batch.jar \
     --spring.profiles.active=cron --spring.main.web-application-type=none
```

Esempio di crontab per un'esecuzione ogni 10 minuti, con le altre proprietà in un file esterno:

```cron
*/10 * * * * /usr/bin/java -Dloader.path=/opt/jdbc-drivers -jar /opt/govpay-aca-batch/govpay-aca-batch.jar --spring.profiles.active=cron --spring.main.web-application-type=none --spring.config.additional-location=/etc/govpay-aca/application.properties
```

### War su application server

Con il profilo `war` si ottiene `target/govpay-aca-batch.war`, da installare su un application server (es. Tomcat 11). Il war non contiene i driver JDBC e usa il datasource dell'application server, indicato con `spring.datasource.jndi-name` (es. `java:comp/env/jdbc/govpay`); le altre proprietà si passano come proprietà di sistema della JVM o variabili d'ambiente. Il batch gira con lo scheduler interno e l'API REST è sotto il context path del war (es. `/govpay-aca-batch/api/batch`).

Il war non è pubblicato nelle release né nell'immagine Docker: si produce con la build locale.

## Esecuzione con Docker

L'immagine è pubblicata su Docker Hub come [linkitaly/govpay-aca-batch](https://hub.docker.com/r/linkitaly/govpay-aca-batch) (release) e `linkitaly/govpay-aca-batch-dev` (sviluppo, a ogni push su `main`). I driver JDBC si montano in `/opt/jdbc-drivers`.

La modalità dipende da `GOVPAY_ACA_BATCH_USA_CRON`: **non impostata, il container esegue il job una volta e termina** (profilo `cron`, senza server HTTP), adatto a un cron esterno o a un CronJob Kubernetes; con `true` resta attivo con lo scheduler interno e risponde sulla porta `10001`.

Esecuzione singola:

```bash
docker run --rm \
  -v ./jdbc-drivers:/opt/jdbc-drivers \
  -e GOVPAY_DB_TYPE=postgresql \
  -e GOVPAY_DB_SERVER=postgres:5432 \
  -e GOVPAY_DB_NAME=govpay \
  -e GOVPAY_DB_USER=govpay \
  -e GOVPAY_DB_PASSWORD=<password> \
  linkitaly/govpay-aca-batch
```

Scheduler interno, con Docker Compose:

```yaml
services:
  govpay-aca:
    image: linkitaly/govpay-aca-batch
    volumes:
      - ./jdbc-drivers:/opt/jdbc-drivers
    environment:
      GOVPAY_DB_TYPE: postgresql
      GOVPAY_DB_SERVER: postgres:5432
      GOVPAY_DB_NAME: govpay
      GOVPAY_DB_USER: govpay
      GOVPAY_DB_PASSWORD: ${GOVPAY_DB_PASSWORD}
      GOVPAY_ACA_BATCH_USA_CRON: "true"
      GOVPAY_ACA_BATCH_INTERVALLO_CRON: 10   # minuti
    ports:
      - "10001:10001"
```

Esecuzione singola pianificata da un CronJob Kubernetes:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: govpay-aca-batch
spec:
  schedule: "*/10 * * * *"
  concurrencyPolicy: Forbid
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: govpay-aca
            image: linkitaly/govpay-aca-batch
            envFrom:
            - configMapRef:
                name: govpay-aca-config
            - secretRef:
                name: govpay-aca-secrets
            volumeMounts:
            - name: jdbc-drivers
              mountPath: /opt/jdbc-drivers
          volumes:
          - name: jdbc-drivers
            persistentVolumeClaim:
              claimName: govpay-jdbc-drivers
          restartPolicy: OnFailure
```

Variabili d'ambiente dell'immagine, oltre alle proprietà Spring Boot in forma di variabile elencate in [Configurazione](#configurazione):

| Variabile | Obbligatoria | Default | Descrizione |
|-----------|--------------|---------|-------------|
| `GOVPAY_DB_TYPE` | sì | - | Tipo di database: `postgresql`, `mysql`, `mariadb`, `oracle` |
| `GOVPAY_DB_SERVER` | sì | - | Server del database, `host` o `host:porta` (porta di default del database se omessa) |
| `GOVPAY_DB_NAME` | sì | - | Nome del database (per Oracle, service name o SID) |
| `GOVPAY_DB_USER` | sì | - | Utente del database |
| `GOVPAY_DB_PASSWORD` | sì | - | Password del database (se manca l'avvio prosegue con un avviso) |
| `GOVPAY_DS_CONN_PARAM` | no | - | Parametri aggiunti all'URL JDBC (es. `zeroDateTimeBehavior=convertToNull` per MySQL) |
| `GOVPAY_DS_JDBC_LIBS` | no | `/opt/jdbc-drivers` | Directory dei driver JDBC |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | no | `servicename` | Formato dell'URL Oracle: `servicename` o `sid` |
| `ORACLE_TNS_ADMIN` | no | - | Directory dei file TNS di Oracle |
| `GOVPAY_ACA_MIN_POOL` | no | `2` | Connessioni minime inattive del pool |
| `GOVPAY_ACA_MAX_POOL` | no | `10` | Connessioni massime del pool |
| `GOVPAY_ACA_BATCH_USA_CRON` | no | - | `true`, `si`, `yes` o `1`: scheduler interno, il container resta attivo. Altrimenti esecuzione singola con il profilo `cron`, senza server HTTP: il container termina a fine job |
| `GOVPAY_ACA_BATCH_INTERVALLO_CRON` | no | `5` | Minuti tra un'esecuzione e l'altra con lo scheduler interno (imposta `scheduler.gpdSenderJob.fixedDelayString`) |
| `SERVER_PORT` | no | `10001` | Porta HTTP con lo scheduler interno |
| `MANAGEMENT_SERVER_PORT` | no | porta del servizio | Porta dedicata agli endpoint actuator |
| `MANAGEMENT_ENDPOINTS` | no | `health,info,metrics,prometheus` | Endpoint actuator esposti |
| `LOG_LEVEL_REFRESH_INTERVAL` | no | `60s` | Intervallo di rilettura dei livelli di log dal database |
| `GOVPAY_ACA_POP_DB_SKIP` | no | `TRUE` | `FALSE` crea all'avvio le tabelle Spring Batch e le viste del batch che mancano |
| `GOVPAY_ACA_DB_CHECK_TABLE` | no | `batch_job_execution_context` | Tabella che indica che lo schema Spring Batch è già presente |
| `GOVPAY_ACA_LIVE_DB_CHECK_SKIP` | no | `FALSE` | Salta l'attesa della porta del database prima dell'inizializzazione |
| `GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY` | no | `30` | Tentativi di connessione alla porta del database |
| `GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT` | no | `5` | Timeout di ogni tentativo di connessione, in secondi |
| `GOVPAY_ACA_READY_DB_CHECK_SKIP` | no | `FALSE` | Salta la verifica degli oggetti già presenti e crea comunque schema e viste |
| `GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY` | no | `5` | Tentativi della verifica |
| `GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME` | no | `2` | Secondi tra un tentativo e l'altro |
| `GOVPAY_ACA_JVM_MAX_RAM_PERCENTAGE` | no | `80` | `-XX:MaxRAMPercentage` della JVM |
| `GOVPAY_ACA_JVM_INITIAL_RAM_PERCENTAGE` | no | - | `-XX:InitialRAMPercentage` della JVM |
| `GOVPAY_ACA_JVM_MIN_RAM_PERCENTAGE` | no | - | `-XX:MinRAMPercentage` della JVM |
| `GOVPAY_ACA_JVM_MAX_METASPACE_SIZE` | no | - | `-XX:MaxMetaspaceSize` della JVM |
| `GOVPAY_ACA_JVM_MAX_DIRECT_MEMORY_SIZE` | no | - | `-XX:MaxDirectMemorySize` della JVM |
| `JAVA_OPTS` | no | - | Opzioni aggiuntive della JVM |

L'identificativo del nodo (`it.govpay.gpd.batch.clusterId`) è impostato dall'entrypoint, con la variabile `IT_GOVPAY_GPD_BATCH_CLUSTERID`, all'indirizzo IP del container o al suo hostname se l'IP non si ricava: un valore passato dall'esterno viene sovrascritto. Le altre variabili che compaiono negli script dell'immagine (es. `INTERVALLO_MINUTI`, `HSQLDB_SHA256`, `GOVPAY_ACA_HOME`) sono interne e non vanno impostate.

Costruzione dell'immagine in locale, scansione delle vulnerabilità e pacchetti inclusi sono descritti in [docker/DOCKER.md](docker/DOCKER.md) e [docker/SCANSIONE.md](docker/SCANSIONE.md).

## Monitoraggio

Con lo scheduler interno gli endpoint actuator rispondono di default sulla porta del servizio (`8080` con il jar, `10001` nel container):

```bash
curl http://localhost:10001/actuator/health       # stato, compresa la connessione al database
curl http://localhost:10001/actuator/prometheus   # metriche in formato Prometheus
```

Per spostarli su una porta dedicata impostare `management.server.port` (nel container `MANAGEMENT_SERVER_PORT`). L'endpoint `loggers`, che permette di cambiare i livelli di log a caldo, non è esposto di default perché il servizio non ha autenticazione: abilitarlo solo insieme a una porta dedicata e non raggiungibile dall'esterno, per esempio `MANAGEMENT_SERVER_PORT=9090` e `MANAGEMENT_ENDPOINTS=health,info,metrics,prometheus,loggers`. In alternativa i livelli si cambiano dalla console, tramite la tabella `configurazione`.

In esecuzione singola non c'è server HTTP: l'esito si ricava dall'exit code del processo e dai log.

## Avvio manuale via REST

Con lo scheduler interno il batch espone, sotto `/api/batch`, l'API comune ai batch GovPay (`AbstractBatchController` di `govpay-common`). Non ha autenticazione: va esposta solo sulla rete interna.

| Metodo e path | Descrizione |
|---------------|-------------|
| `GET /api/batch/run` | Avvia il job in modo asincrono (`202`, oppure `409` se è già in esecuzione) |
| `GET /api/batch/run?force=true` | Avvia il job anche se risulta un'esecuzione in corso |
| `GET /api/batch/status` | Stato corrente del job |
| `GET /api/batch/lastExecution` | Ultima esecuzione completata |
| `GET /api/batch/nextExecution` | Prossima esecuzione stimata |
| `GET /api/batch/info` | Nome e descrizione del batch |
| `GET /api/batch/executions` | Elenco paginato delle esecuzioni |
| `GET /api/batch/executions/{executionId}` | Dettaglio di un'esecuzione |
| `DELETE /api/batch/executions/{executionId}` | Richiede l'arresto di un'esecuzione |
| `GET /api/batch/cache/clear` | Svuota la cache dei connettori |

```bash
curl http://localhost:10001/api/batch/run
curl http://localhost:10001/api/batch/status
```

## Elaborazione

Il job ha un solo step chunk-oriented (`spedizionePendenzaStep`):

- **Reader**: legge da `v_versamenti_gpd` le pendenze in stato `NON_ESEGUITO` o `ANNULLATO` con `data_ultima_modifica_aca` negli ultimi `numeroGiorni` giorni e successiva a `data_ultima_comunicazione_aca` (o mai comunicate).
- **Processor** (`SendPendenzaToGpdProcessor`), eseguito in modo asincrono:
  - pendenza mai comunicata: `POST` della posizione sul GPD; se la posizione risulta in `DRAFT` (anche quando esiste già, `409`) la pubblica con `POST .../publish`;
  - pendenza `NON_ESEGUITO` già comunicata: `PUT` della posizione, con pubblicazione;
  - pendenza `ANNULLATO` già comunicata: `PUT` della posizione senza pubblicazione, che la riporta in `DRAFT`;
  - le pendenze senza IUV, con scadenza passata o con metadati non leggibili non vengono inviate e sono segnate come comunicate; un `404` o un `409` sull'aggiornamento chiudono la pendenza (posizione inesistente o già chiusa sul GPD).
- **Writer** (`PendenzaWriter`): imposta `data_ultima_comunicazione_aca` all'avvio dell'esecuzione per le pendenze inviate o chiuse. Quelle fallite per errori di rete, `5xx` o `403` (con `it.govpay.gpd.batch.policy.reinvio.403.enabled=false`) restano da inviare e sono riprese all'esecuzione successiva.

Ogni chiamata al GPD produce un evento nel GDE con richiesta, risposta ed esito.

## Licenza

GovPay ACA Batch è distribuito con licenza [GNU General Public License v3.0](LICENSE).
