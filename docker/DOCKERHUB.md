<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay A.C.A. Batch

[![GitHub](https://img.shields.io/badge/GitHub-link--it%2Fgovpay--aca--batch-blue?logo=github)](https://github.com/link-it/govpay-aca-batch)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

Batch Spring Boot per l'alimentazione dell'**Archivio Centralizzato Avvisi (A.C.A.)** di pagoPA.

## Cos'è GovPay A.C.A. Batch

GovPay A.C.A. Batch è un componente del progetto [GovPay](https://github.com/link-it/govpay) che si occupa della sincronizzazione automatica delle posizioni debitorie verso il servizio ACA di pagoPA.

### Funzionalità principali

- Sincronizzazione automatica delle posizioni debitorie verso pagoPA
- Supporto multi-database: PostgreSQL, MySQL/MariaDB, Oracle
- Modalità di deployment flessibili (daemon o esecuzione singola)
- Integrazione opzionale con GDE (Giornale degli Eventi)
- Health check e monitoraggio tramite Spring Boot Actuator
- Gestione automatica del recovery per job bloccati

## Versioni disponibili

- `latest` - ultima versione stabile
- `1.1.4`

Storico completo delle modifiche consultabile nel [ChangeLog](https://github.com/link-it/govpay-aca-batch/blob/main/ChangeLog) del progetto.

## Quick Start

```bash
docker pull linkitaly/govpay-aca-batch:latest
```

## Documentazione

- [README e istruzioni di configurazione](https://github.com/link-it/govpay-aca-batch/blob/main/README.md)
- [Documentazione Docker](https://github.com/link-it/govpay-aca-batch/blob/main/docker/DOCKER.md)
- [Dockerfile](https://github.com/link-it/govpay-aca-batch/blob/main/docker/govpay-aca/Dockerfile.github)

## Licenza

GovPay A.C.A. Batch è rilasciato con licenza [GPL v3](https://www.gnu.org/licenses/gpl-3.0).

## Supporto

- **Issues**: [GitHub Issues](https://github.com/link-it/govpay-aca-batch/issues)
- **GovPay**: [govpay.readthedocs.io](https://govpay.readthedocs.io/)

---

Sviluppato da [Link.it s.r.l.](https://www.link.it)
