<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay - Porta di accesso al sistema pagoPA - ACA Batch

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay-aca-batch&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay-aca-batch)
[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-aca-batch?label=Docker%20Hub&sort=semver)](https://hub.docker.com/r/linkitaly/govpay-aca-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay-aca-batch/main/LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.8-brightgreen.svg)](https://spring.io/projects/spring-boot)

## Sommario

Batch Spring Boot per l'alimentazione dell'Archivio Centralizzato Avvisi pagoPA.

## Requisiti

- JDK 21
- Maven 3.8+
- Database supportati: PostgreSQL, MySQL/MariaDB, Oracle

## Compilazione

Il progetto utilizza librerie Spring Boot versione 3.5.x e JDK 21.

Per la compilazione eseguire il seguente comando, verranno eseguiti anche i test:

```bash
mvn clean install -P [jar|war]
```

Il profilo permette di selezionare il packaging dei progetti (jar o war).

## Esecuzione

### 1. Modalità Standalone

Per l'avvio dell'applicativo come standalone con scheduler interno:

```bash
mvn spring-boot:run
```

Per sovrascrivere le proprietà definite nel file `application.properties`:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.datasource.url=[NUOVO_VALORE] ..."
```

### 2. Modalità Cron

Avvio dell'applicazione per essere utilizzata all'interno di un cron esterno. Il batch viene eseguito una volta e l'applicazione termina:

```bash
java -Dspring.profiles.active=cron -jar target/govpay-aca-batch.jar \
  --spring.batch.job.enabled=false \
  --spring.main.web-application-type="none" \
  --spring.datasource.url="[URL CONNESSIONE DB]" \
  --spring.datasource.driverClassName="[CLASSE DRIVER JDBC]" \
  --spring.datasource.username="[USERNAME DB]" \
  --spring.datasource.password="[PASSWORD DB]" \
  --spring.jpa.database-platform="[DIALECT JPA]" \
  --spring.jpa.properties.hibernate.dialect="[DIALECT JPA]" \
  --it.govpay.gpd.time-zone="Europe/Rome" \
  --it.govpay.gpd.batch.client.header.subscriptionKey.name="Ocp-Apim-Subscription-Key" \
  --it.govpay.gpd.batch.client.header.subscriptionKey.value="[VALORE SUBSCRIPTION-KEY]" \
  --it.govpay.gpd.batch.client.debugging=[true|false] \
  --it.govpay.gpd.batch.client.baseUrl="[BASE URL SERVIZIO GPD PAGOPA]" \
  --it.govpay.gpd.batch.jobs.gpdSenderJob.steps.spedizionePendenzaStep.chunk-size=10 \
  --it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni=7 \
  --it.govpay.gpd.batch.clusterId=[CLUSTER_ID] \
  --it.govpay.gpd.batch.stale-threshold-minutes=120 \
  --it.govpay.gpd.batch.policy.reinvio.403.enabled=[true|false] \
  --it.govpay.gde.enabled=[true|false] \
  --it.govpay.gde.client.baseUrl=[BASE URL SERVIZIO GDE]
```

Esempio di configurazione crontab per esecuzione ogni 10 minuti:

```cron
*/10 * * * * /usr/bin/java -Dspring.profiles.active=cron -jar /opt/govpay-aca-batch/govpay-aca-batch.jar [OPZIONI...]
```

### 3. Docker

L'immagine Docker è disponibile su Docker Hub:

```bash
docker pull linkitaly/govpay-aca-batch:latest
```

#### Esempio con Docker Run (esecuzione singola)

```bash
docker run --rm \
  -e GOVPAY_DB_TYPE=[postgresql|mysql|mariadb|oracle] \
  -e GOVPAY_DB_SERVER=[HOST:PORTA] \
  -e GOVPAY_DB_NAME=[NOME DATABASE] \
  -e GOVPAY_DB_USER=[USERNAME DB] \
  -e GOVPAY_DB_PASSWORD=[PASSWORD DB] \
  -e GOVPAY_ACA_GPD_ENV=[prod|uat] \
  -e GOVPAY_ACA_GPD_SUBSCRIPTIONKEY=[VALORE SUBSCRIPTION-KEY] \
  linkitaly/govpay-aca-batch:latest
```

#### Esempio con Docker Compose (modalità cron interno)

```yaml
version: '3.8'
services:
  govpay-aca:
    image: linkitaly/govpay-aca-batch:latest
    environment:
      # Database
      GOVPAY_DB_TYPE: [postgresql|mysql|mariadb|oracle]
      GOVPAY_DB_SERVER: [HOST:PORTA]
      GOVPAY_DB_NAME: [NOME DATABASE]
      GOVPAY_DB_USER: [USERNAME DB]
      GOVPAY_DB_PASSWORD: [PASSWORD DB]
      # GPD pagoPA
      GOVPAY_ACA_GPD_ENV: [prod|uat]
      GOVPAY_ACA_GPD_SUBSCRIPTIONKEY: [VALORE SUBSCRIPTION-KEY]
      # Modalità cron interno
      GOVPAY_ACA_BATCH_USA_CRON: "true"
      GOVPAY_ACA_BATCH_INTERVALLO_CRON: 5  # minuti
    ports:
      - "10001:10001"  # Actuator health check
```

#### Esempio Kubernetes CronJob

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: govpay-aca-batch
spec:
  schedule: "*/10 * * * *"  # Ogni 10 minuti
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: govpay-aca
            image: linkitaly/govpay-aca-batch:latest
            envFrom:
            - configMapRef:
                name: govpay-aca-config
            - secretRef:
                name: govpay-aca-secrets
          restartPolicy: OnFailure
```

Per la documentazione completa sulla configurazione Docker, consultare [docker/DOCKER.md](docker/DOCKER.md).

## Configurazione

All'interno del file `application.properties` sono definite le seguenti proprietà:

### Proprietà Server

```properties
# ----------- SPRING SERVLET ------------

server.port=[PORTA SERVIZIO STANDALONE]

# Abilitazione Endpoint /actuator/health/liveness
management.endpoints.web.base-path=[BASEPATH SERVIZI STATO APPLICAZIONE]
```

### Proprietà Database

```properties
# ------------ HIBERNATE & JPA -------------------

# Configurazione DB
#spring.datasource.jndiName=[JNDI NAME DEL DATASOURCE]
spring.datasource.url=[URL CONNESSIONE DB]
spring.datasource.driverClassName=[CLASSE DRIVER JDBC]
spring.datasource.username=[USERNAME DB]
spring.datasource.password=[PASSWORD DB]

spring.jpa.database-platform=[DIALECT JPA]
spring.jpa.properties.hibernate.dialect=[DIALECT JPA]

spring.jpa.hibernate.ddl-auto=[COMPORTAMENTO HIBERNATE GENERAZIONE SCHEMA]
```

### Proprietà GPD pagoPA

```properties
# -------------- GPD PAGOPA ----------------

# Informazioni per la connessione verso PagoPA
it.govpay.gpd.batch.client.header.subscriptionKey.name=[NOME HEADER SUBSCRIPTION-KEY]
it.govpay.gpd.batch.client.header.subscriptionKey.value=[VALORE SUBSCRIPTION-KEY]
it.govpay.gpd.batch.client.debugging=[true|false]
it.govpay.gpd.batch.client.baseUrl=[BASE URL SERVIZIO GPD PAGOPA]
```

**URL ambienti pagoPA:**

| Ambiente | URL |
|----------|-----|
| Produzione | `https://api.platform.pagopa.it/aca/debt-positions-service/v1/` |
| UAT/Collaudo | `https://api.uat.platform.pagopa.it/aca/debt-positions-service/v1/` |

### Proprietà Batch

```properties
# -------------- BATCH ----------------

# TimeZone applicazione
it.govpay.gpd.time-zone=Europe/Rome

# Dimensione del chunk di lavoro
it.govpay.gpd.batch.jobs.gpdSenderJob.steps.spedizionePendenzaStep.chunk-size=[DIMENSIONE CHUNK]

# Numero di giorni su cui limitare la ricerca delle pendenze da spedire al ACA
it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni=[LIMITE TEMPORALE RICERCA PENDENZE]

# Cluster ID (identificativo del nodo in ambiente cluster)
it.govpay.gpd.batch.clusterId=[CLUSTER_ID]

# Soglia di inattività in minuti per considerare un job come stale
# Un job che non viene aggiornato (LAST_UPDATED) da più di questo tempo viene considerato bloccato
it.govpay.gpd.batch.stale-threshold-minutes=[MINUTI SOGLIA INATTIVITA]

# Policy reinvio per il codice di errore 403
it.govpay.gpd.batch.policy.reinvio.403.enabled=[true|false]
```

### Proprietà GDE (Giornale degli Eventi)

```properties
# -------------- GDE ----------------

# Abilita il servizio GDE
it.govpay.gde.enabled=[true|false]

# Base URL del servizio GDE
it.govpay.gde.client.baseUrl=[BASE URL SERVIZIO GDE]
```

## Licenza

Questo progetto è rilasciato sotto licenza GPL-3.0. Vedere il file [LICENSE](LICENSE) per i dettagli.

## Supporto

- **Issues**: https://github.com/link-it/govpay-aca-batch/issues
- **GovPay**: https://github.com/link-it/govpay
- **Documentazione pagoPA**: https://docs.pagopa.it/
