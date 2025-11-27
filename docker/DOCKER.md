# GovPay A.C.A. (Avvisi Cortesia Automatici) - Configurazione Docker

Containerizzazione Docker per il batch GovPay A.C.A. che gestisce l'invio automatico di avvisi di cortesia per posizioni debitorie tramite il servizio GPD (Gestione Posizioni Debitorie) di pagoPA.

## Panoramica

Questa configurazione Docker fornisce:
- Supporto multi-database (PostgreSQL, MySQL/MariaDB, Oracle)
- Modalità di deployment flessibili (schedulato da orchestratore o modalita cron)
- Integrazione con il servizio GPD di pagoPA
- Integrazione opzionale con GDE (Giornale degli Eventi)
- Health check e monitoraggio
- Connection pooling configurabile

## Avvio Rapido

### 1. Costruire l'Immagine Docker

```bash
# Build con supporto PostgreSQL (predefinito)
./build_image.sh
```

### 2. Configurazione obbligatoria
- `GOVPAY_DB_TYPE`: Tipo database (postgresql, mysql, mariadb, oracle)
- `GOVPAY_DB_SERVER`: Server database (formato: host:porta)
- `GOVPAY_DB_NAME`: Nome del database
- `GOVPAY_DB_USER`: Username del database
- `GOVPAY_DB_PASSWORD`: Password del database
- `GOVPAY_ACA_GPD_ENV`: Ambiente GPD (prod, uat, collaudo) o `GOVPAY_ACA_GPD_CUSTOMURL` per URL personalizzato
- `GOVPAY_ACA_GPD_SUBSCRIPTIONKEY`: Chiave di sottoscrizione API pagoPA

### 3. Avviare i Servizi

```bash
# Avvio con docker-compose
docker compose up -d

# Visualizzare i log
docker compose logs -f govpay-aca

# Verificare lo stato
docker compose ps
```

## Architettura

```
┌─────────────────────────────────────┐
│   GovPay A.C.A. Batch Processor    │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   Applicazione Spring Boot   │  │
│  │   - Spring Batch             │  │
│  │   - Client GPD               │  │
│  │   - Client GDE (opzionale)   │  │
│  └──────────────────────────────┘  │
│              │                      │
│              ▼                      │
│  ┌──────────────────────────────┐  │
│  │   HikariCP Connection Pool   │  │
│  └──────────────────────────────┘  │
└─────────────────┬───────────────────┘
                  │
                  ▼
      ┌───────────────────────┐
      │   Database (RDBMS)    │
      │  - PostgreSQL         │
      │  - MySQL/MariaDB      │
      │  - Oracle             │
      └───────────────────────┘
```

## Modalità di Deployment

### Modalità CRON (Auto-schedulata)

Esecuzione continua con job batch auto-schedulati tramite Spring Scheduler:

```env
GOVPAY_ACA_BATCH_USA_CRON=true
GOVPAY_ACA_BATCH_INTERVALLO_CRON=5  # Intervallo in minuti (default: 5)
SERVER_PORT=10001  # Porta per Actuator (default: 10001)
```

**Caratteristiche:**
- Il container rimane attivo come daemon
- Scheduler interno esegue il batch ad intervalli regolari
- Espone endpoint Spring Boot Actuator per health check e metriche
- Ideale per deployment con orchestratori (Kubernetes, Docker Swarm, systemd)

### Modalità GESTITO (Schedulazione Esterna)

Esegue il batch una volta ed esce (per schedulazione esterna tramite cron, Kubernetes CronJob, ecc.):

```env
GOVPAY_ACA_BATCH_USA_CRON=false  # o non impostare la variabile
```

**Caratteristiche:**
- Il container esegue il batch ed esce immediatamente
- Non espone endpoint Actuator
- La schedulazione è gestita esternamente (cron, orchestratore)
- Ideale per CronJob di Kubernetes o cron di sistema

Esempio di configurazione cron:
```bash
# Esecuzione ogni ora
0 * * * * docker run --rm --env-file .env govpay-aca-batch:latest
```

Esempio CronJob Kubernetes:
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: govpay-aca-batch
spec:
  schedule: "0 * * * *"  # Ogni ora
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: govpay-aca
            image: govpay-aca-batch:latest
            envFrom:
            - configMapRef:
                name: govpay-aca-config
          restartPolicy: OnFailure
```

## Configurazione Database

Il container supporta la nomenclatura delle variabili compatibile con govpay-docker. L'URL JDBC viene costruito automaticamente in base al tipo di database.

### PostgreSQL (Default)

```env
GOVPAY_DB_TYPE=postgresql
GOVPAY_DB_SERVER=postgres:5432
GOVPAY_DB_NAME=govpay
GOVPAY_DB_USER=govpay
GOVPAY_DB_PASSWORD=yourpassword
```

**Parametri opzionali:**
```env
GOVPAY_DS_CONN_PARAM=sslmode=require&connectTimeout=10  # Parametri JDBC aggiuntivi
GOVPAY_ACA_MIN_POOL=2  # Connessioni minime nel pool (default: 2)
GOVPAY_ACA_MAX_POOL=10  # Connessioni massime nel pool (default: 10)
```

### MySQL/MariaDB

```env
GOVPAY_DB_TYPE=mysql  # oppure mariadb
GOVPAY_DB_SERVER=mysql:3306
GOVPAY_DB_NAME=govpay
GOVPAY_DB_USER=govpay
GOVPAY_DB_PASSWORD=yourpassword
```

**Parametri opzionali:**
```env
GOVPAY_DS_CONN_PARAM=zeroDateTimeBehavior=convertToNull&useSSL=false
```

### Oracle

```env
GOVPAY_DB_TYPE=oracle
GOVPAY_DB_SERVER=oracle:1521
GOVPAY_DB_NAME=XE  # Service name o SID
GOVPAY_DB_USER=govpay
GOVPAY_DB_PASSWORD=yourpassword
```

**Configurazione Service Name (default):**
```env
GOVPAY_ORACLE_JDBC_URL_TYPE=servicename  # Genera: jdbc:oracle:thin:@//host:port/service
```

**Configurazione SID:**
```env
GOVPAY_ORACLE_JDBC_URL_TYPE=sid  # Genera: jdbc:oracle:thin:@host:port:sid
```

**Nota:** Il formato con TNS Names non è attualmente supportato dalla configurazione automatica. Per utilizzare TNS Names, è necessario costruire manualmente l'URL JDBC.

### Inizializzazione Database Automatica

Il container può inizializzare automaticamente le tabelle del batch se non esistono:

```env
GOVPAY_ACA_POP_DB_SKIP=FALSE  # Abilita inizializzazione (default: TRUE)
GOVPAY_ACA_DB_CHECK_TABLE=batch_job_execution_context  # Tabella di riferimento
```

**Configurazione health check:**
```env
# Liveness check (connessione TCP al DB)
GOVPAY_ACA_LIVE_DB_CHECK_SKIP=FALSE
GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY=30
GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME=2
GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT=5

# Readiness check (verifica esistenza tabelle)
GOVPAY_ACA_READY_DB_CHECK_SKIP=FALSE
GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY=5
GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME=2
```

## Integrazione con GDE (Giornale degli Eventi)

L'integrazione con GDE è disabilitata di default. Per abilitarla, specificare l'URL del servizio:

```env
GOVPAY_ACA_GDE_URL=http://govpay-gde-api:8080
```

Quando `GOVPAY_ACA_GDE_URL` è impostato, l'integrazione GDE viene automaticamente abilitata e il batch registrerà gli eventi nel giornale.

## Configuration Reference

### Variabili d'Ambiente - Database

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOVPAY_DB_TYPE` | **Yes** | - | Tipo database: `postgresql`, `mysql`, `mariadb`, `oracle` |
| `GOVPAY_DB_SERVER` | **Yes** | - | Server database nel formato `host:porta` |
| `GOVPAY_DB_NAME` | **Yes** | - | Nome del database o service name |
| `GOVPAY_DB_USER` | **Yes** | - | Username del database |
| `GOVPAY_DB_PASSWORD` | **Yes** | - | Password del database |
| `GOVPAY_DS_CONN_PARAM` | No | - | Parametri aggiuntivi URL JDBC (es: `sslmode=require`) |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | No | `servicename` | Oracle: `servicename` o `sid` |
| `GOVPAY_ACA_MIN_POOL` | No | `2` | Connessioni minime pool HikariCP |
| `GOVPAY_ACA_MAX_POOL` | No | `10` | Connessioni massime pool HikariCP |
| `GOVPAY_DS_JDBC_LIBS` | No | `/opt/jdbc-drivers` | Percorso driver JDBC |

### Variabili d'Ambiente - GPD (pagoPA)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOVPAY_ACA_GPD_ENV` | **Yes*** | - | Ambiente GPD: `prod`, `produzione`, `uat`, `collaudo`, `test`, `stage` |
| `GOVPAY_ACA_GPD_CUSTOMURL` | **Yes*** | - | URL personalizzato GPD (alternativo a `GOVPAY_ACA_GPD_ENV`) |
| `GOVPAY_ACA_GPD_SUBSCRIPTIONKEY` | **Yes** | - | Subscription Key API pagoPA |

**Nota:* È richiesto `GOVPAY_ACA_GPD_ENV` **oppure** `GOVPAY_ACA_GPD_CUSTOMURL` (non entrambi)

### Variabili d'Ambiente - Modalità Deployment

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOVPAY_ACA_BATCH_USA_CRON` | No | `false` | Modalità CRON: `true`, `si`, `yes`, `1` |
| `GOVPAY_ACA_BATCH_INTERVALLO_CRON` | No | `5` | Intervallo scheduler in minuti (modalità CRON) |
| `SERVER_PORT` | No | `10001` | Porta Actuator (modalità CRON) |

### Variabili d'Ambiente - Integrazione GDE

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOVPAY_ACA_GDE_URL` | No | - | URL servizio GDE (se impostato, GDE viene abilitato) |

### Variabili d'Ambiente - Inizializzazione Database

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOVPAY_ACA_POP_DB_SKIP` | No | `TRUE` | Salta inizializzazione DB: `TRUE`/`FALSE` |
| `GOVPAY_ACA_DB_CHECK_TABLE` | No | `batch_job_execution_context` | Tabella per readiness check |
| `GOVPAY_ACA_LIVE_DB_CHECK_SKIP` | No | `FALSE` | Salta liveness check (TCP) |
| `GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY` | No | `30` | Tentativi max liveness check |
| `GOVPAY_ACA_LIVE_DB_CHECK_SLEEP_TIME` | No | `2` | Secondi tra tentativi liveness |
| `GOVPAY_ACA_LIVE_DB_CHECK_CONNECT_TIMEOUT` | No | `5` | Timeout connessione TCP (secondi) |
| `GOVPAY_ACA_READY_DB_CHECK_SKIP` | No | `FALSE` | Salta readiness check (tabelle) |
| `GOVPAY_ACA_READY_DB_CHECK_MAX_RETRY` | No | `5` | Tentativi max readiness check |
| `GOVPAY_ACA_READY_DB_CHECK_SLEEP_TIME` | No | `2` | Secondi tra tentativi readiness |

### Variabili d'Ambiente - JVM Memory

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GOVPAY_ACA_JVM_MAX_RAM_PERCENTAGE` | No | `80` | Percentuale RAM massima utilizzabile dalla JVM |
| `GOVPAY_ACA_JVM_INITIAL_RAM_PERCENTAGE` | No | - | Percentuale RAM iniziale |
| `GOVPAY_ACA_JVM_MIN_RAM_PERCENTAGE` | No | - | Percentuale RAM minima |
| `GOVPAY_ACA_JVM_MAX_METASPACE_SIZE` | No | - | Dimensione max Metaspace (es: `256m`) |
| `GOVPAY_ACA_JVM_MAX_DIRECT_MEMORY_SIZE` | No | - | Dimensione max Direct Memory (es: `128m`) |
| `JAVA_OPTS` | No | - | Opzioni aggiuntive JVM |


### Health Check

In modalità CRON, il container espone gli endpoint Spring Boot Actuator:

```bash
# Health check
curl http://localhost:10001/actuator/health

# Verifica dettagliata
curl http://localhost:10001/actuator/health/liveness
curl http://localhost:10001/actuator/health/readiness
```

**Nota:** La porta di default è `10001` (configurabile tramite `SERVER_PORT`)

### Logs

```bash
# Seguire i log del container
docker-compose logs -f govpay-aca

# Log dall'interno del container
docker exec -it govpay-aca-batch tail -f /var/log/govpay/govpay-aca.log

# Debug dell'entrypoint (per troubleshooting avvio)
docker exec -it govpay-aca-batch cat /tmp/entrypoint_debug.log
```

### Metrics

Accesso alle metriche Spring Boot (solo modalità CRON):
```bash
# Metriche generali
curl http://localhost:10001/actuator/metrics

# Metriche specifiche
curl http://localhost:10001/actuator/metrics/jvm.memory.used
curl http://localhost:10001/actuator/metrics/hikaricp.connections.active
```

## File Structure

```
govpay-aca-batch/docker/
├── govpay-aca
|   └── Dockerfile.github          # Definizione immagine Docker
├── build_image.sh                 # Script di build
├── DOCKER.md                      # Questa documentazione
└── commons/
    ├── entrypoint.sh             # Script di avvio container
    └── init_aca_db.sh            # Script inizializzazione Database
```

## Ambienti PagoPA

Il container supporta la configurazione automatica degli ambienti pagoPA tramite la variabile `GOVPAY_ACA_GPD_ENV`:

### Produzione
```env
GOVPAY_ACA_GPD_ENV=prod
# oppure
GOVPAY_ACA_GPD_ENV=produzione
```
**URL generato:** `https://api.platform.pagopa.it/gpd/api/v1`

### UAT/Collaudo
```env
GOVPAY_ACA_GPD_ENV=uat
# oppure uno di: collaudo, test, stage
```
**URL generato:** `https://api.uat.platform.pagopa.it/gpd/api/v1`

### URL Personalizzato
Per ambienti non standard o on-premise:
```env
GOVPAY_ACA_GPD_CUSTOMURL=https://custom-gpd-server.example.com/gpd/api/v1
```

## Esempi di Configurazione

### Esempio Minimo (PostgreSQL + UAT)

```env
# Database
GOVPAY_DB_TYPE=postgresql
GOVPAY_DB_SERVER=postgres:5432
GOVPAY_DB_NAME=govpay
GOVPAY_DB_USER=govpay
GOVPAY_DB_PASSWORD=secret123

# GPD pagoPA
GOVPAY_ACA_GPD_ENV=uat
GOVPAY_ACA_GPD_SUBSCRIPTIONKEY=your-subscription-key-here

# Modalità auto-schedulata (ogni 5 minuti)
GOVPAY_ACA_BATCH_USA_CRON=true
```

### Esempio Completo (Oracle + Produzione + Inizializzazione DB)

```env
# Database Oracle
GOVPAY_DB_TYPE=oracle
GOVPAY_DB_SERVER=oracle-prod.example.com:1521
GOVPAY_DB_NAME=GOVPAYDB
GOVPAY_DB_USER=govpay_aca
GOVPAY_DB_PASSWORD=SecurePassword123!
GOVPAY_ORACLE_JDBC_URL_TYPE=servicename
GOVPAY_DS_CONN_PARAM=oracle.net.ssl_version=1.2

# Pool connessioni
GOVPAY_ACA_MIN_POOL=5
GOVPAY_ACA_MAX_POOL=20

# Inizializzazione database
GOVPAY_ACA_POP_DB_SKIP=FALSE
GOVPAY_ACA_LIVE_DB_CHECK_MAX_RETRY=60

# GPD pagoPA Produzione
GOVPAY_ACA_GPD_ENV=prod
GOVPAY_ACA_GPD_SUBSCRIPTIONKEY=prod-subscription-key

# GDE
GOVPAY_ACA_GDE_URL=https://govpay-gde.example.com

# Modalità gestita esternamente (CronJob K8s)
GOVPAY_ACA_BATCH_USA_CRON=false

# JVM Memory
GOVPAY_ACA_JVM_MAX_RAM_PERCENTAGE=75
GOVPAY_ACA_JVM_MAX_METASPACE_SIZE=256m
```

## Supporto

Per problemi relativi a:
- **GovPay**: https://github.com/link-it/govpay
- **pagoPA GPD**: https://docs.pagopa.it/
- **Sviluppo Java**: Consultare `README.md`
