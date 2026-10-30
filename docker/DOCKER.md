# GovPay ACA Batch - Immagine Docker

Immagine del batch che alimenta l'Archivio Centralizzato Avvisi (ACA) di pagoPA tramite il servizio GPD. Modalità di esecuzione, esempi (`docker run`, Docker Compose, CronJob Kubernetes) e **l'elenco completo delle variabili d'ambiente** sono nel README del progetto, sezione [Esecuzione con Docker](../README.md#esecuzione-con-docker).

## Costruzione dell'immagine

Lo script `build_image.sh` costruisce l'immagine in due modi:

| Origine | Comando | Dockerfile |
|---------|---------|------------|
| Release GitHub | `./build_image.sh -v <versione>` (senza `-v`, l'ultima release) | `govpay-aca/Dockerfile.github`: scarica jar e `sql.zip` della release e ne verifica lo sha256, letto dalle API delle release o indicato con `-s` e `-q` |
| Build locale | `./build_image.sh -l ../target/govpay-aca-batch.jar` | `govpay-aca/Dockerfile.daFile`: usa il jar indicato e `../target/sql.zip`, prodotti con `mvn clean install -Pjar,dist` |

Altre opzioni: `-t <repository>:<tag>` per il nome dell'immagine (default `linkitaly/govpay-aca-batch:<versione>`), `-e` e `-f` per le directory interne dell'applicazione e dei log. L'elenco completo si ottiene con `./build_image.sh -h`.

## Contenuto dell'immagine

| Percorso | Contenuto |
|----------|-----------|
| `/opt/govpay-aca/govpay-aca-batch.jar` | Applicazione |
| `/opt/sql` | Contenuto di `sql.zip`: schema Spring Batch (`spring-batch/`), viste del batch (`<database>/create-db.sql`), svecchiamento dei metadati (`cleanup/`), versioni (`VERSION`) |
| `/opt/hsqldb-2.7.4` | SqlTool, usato da `init_aca_db.sh` per creare schema e viste |
| `/opt/jdbc-drivers` | Punto di montaggio dei driver JDBC, non inclusi (vedi [jdbc-drivers/README.md](jdbc-drivers/README.md)) |
| `/var/log/govpay-aca/govpay-aca.log` | File di log dell'applicazione |
| `/usr/local/bin/entrypoint.sh` | Costruisce la configurazione dalle variabili d'ambiente e avvia il batch |
| `/usr/local/bin/init_aca_db.sh` | Crea le tabelle Spring Batch e le viste del batch che mancano, se `GOVPAY_ACA_POP_DB_SKIP=FALSE` |

Il container gira con l'utente non privilegiato `govpay`.

## Log

```bash
docker logs -f govpay-aca-batch
docker exec govpay-aca-batch tail -f /var/log/govpay-aca/govpay-aca.log
```

## File

```
docker/
├── build_image.sh            # costruzione dell'immagine
├── scan_image.sh             # scansione delle vulnerabilità (vedi SCANSIONE.md)
├── .env.template             # esempio di variabili per docker run --env-file
├── commons/
│   ├── entrypoint.sh         # avvio del container
│   └── init_aca_db.sh        # inizializzazione del database
├── govpay-aca/
│   ├── Dockerfile.github     # immagine dalla release GitHub
│   └── Dockerfile.daFile     # immagine da un jar locale
└── jdbc-drivers/             # driver JDBC da montare in /opt/jdbc-drivers
```

La scansione delle vulnerabilità dell'immagine è descritta in [SCANSIONE.md](SCANSIONE.md).
