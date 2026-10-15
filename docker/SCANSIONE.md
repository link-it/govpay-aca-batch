# govpay-aca-batch - Scansione dell'immagine Docker

Riferimenti: link-it/govpay-aca-batch#53, pilota link-it/govpay-gde-api#36, issue madre link-it/govpay#895.

Ogni immagine costruita dalla pipeline, di release (su tag) e di sviluppo (su `main`), viene scansionata **prima** di essere pubblicata, con [Trivy](https://trivy.dev) e [Grype](https://github.com/anchore/grype): sistema operativo di base, JRE e librerie dell'applicazione. Un'immagine che non supera il gate non viene pubblicata; un'immagine di release gia' pubblicata non viene piu' toccata.

| | |
|---|---|
| **Gate** (Trivy) | vulnerabilita' CRITICAL e HIGH con una versione corretta disponibile, al netto delle eccezioni |
| **Report** (Grype) | solo informativo, come secondo parere: sui pacchetti Alpine usa la severita' NVD, spesso piu' alta di quella della distribuzione |
| **Senza correzione** | non bloccano; restano nei report a ogni build |

La logica sta in `docker/scan_image.sh`, che si esegue uguale in locale su un'immagine gia' costruita (serve solo docker):

```bash
./docker/scan_image.sh linkitaly/govpay-aca-batch-dev:<versione>
```

I report finiscono in `./image-scan-report` (`SCAN_OUT_DIR` per cambiarla): `riepilogo.md`, `trivy-gate.md` con l'elenco di cio' che blocca, i report completi di Trivy (JSON, SARIF, HTML) e di Grype (JSON, SARIF, testo), e lo SBOM CycloneDX dell'immagine. In pipeline lo stesso riepilogo compare nella pagina della run, i SARIF nella scheda Security del repository, i report come artifact e, su tag, allegati alla release come `image-scan-report-<tag>.zip`; lo SBOM dell'immagine va su Dependency-Track (progetto `govpay-aca-batch-image`).

## Se il gate blocca

| Tipo di segnalazione | Cosa fare |
|---|---|
| Sistema operativo o JRE, con correzione | Rieseguire il build: la base (`pull`) e i pacchetti (`apk upgrade`) vengono aggiornati. Se la correzione non e' ancora nella base, aggiornare l'immagine base nel Dockerfile. |
| Libreria Java, con correzione | Aggiornare la dipendenza in `govpay-bom` o nel `pom.xml`; se la versione e' imposta da un'altra dipendenza, override in `dependencyManagement` con le CVE in commento. Poi nuovo build. |
| Pacchetto non necessario a runtime | Toglierlo dall'immagine. |
| Non sfruttabile nell'uso di govpay-aca-batch, o falso positivo | Eccezione documentata, dopo verifica (vedi sotto). |

**Eccezioni.** Si dichiarano in `.trivyignore.yaml`, nella radice del repository, con motivo, autore, issue di riferimento e **scadenza** (`expired_at`): alla scadenza l'eccezione decade e il gate torna a segnalare. Le stesse eccezioni vanno riportate in `.grype.yaml`, perche' i due report restino confrontabili. Le eccezioni servono a rendere leggibile il gate, non a nascondere i problemi.

**Deroga temporanea.** Se un rilascio urgente e' bloccato da una vulnerabilita' appena pubblicata e non correggibile in tempi brevi, la variabile di repository `IMAGE_SCAN_GATE=warn` porta il gate in sola segnalazione: viene valutato e riportato, ma l'immagine viene pubblicata. La deroga va motivata in una issue e la variabile rimossa subito dopo il rilascio.

## Pacchetti dell'immagine

Oltre alla base `eclipse-temurin:25-jre-alpine`, l'immagine installa solo `bash` (per l'entrypoint), `netcat-openbsd` (per il controllo del database all'avvio, `nc -z`) e `tzdata`. `curl` e `unzip` servono solo al build e vengono rimossi nello stesso passo; `tar` non c'e'. Per una verifica dall'interno del container c'e' il `wget` di busybox, lo stesso usato dagli healthcheck:

```bash
docker exec <container> wget -q -O - http://localhost:10001/actuator/health
```

Le segnalazioni di Grype su `coreutils` e `busybox`, pacchetti della base senza correzione in Alpine, sono eccezioni motivate in `.grype.yaml`.

## Checksum del jar

`docker/govpay-aca/Dockerfile.github` scarica jar e `sql.zip` dalla release GitHub con verifica del certificato e del checksum, passati con i build-arg `govpay_aca_sha256` e `govpay_aca_sql_sha256`: in pipeline sono calcolati dall'artifact della build, `build_image.sh` li legge dal digest degli asset nelle API delle release (opzioni `-s` e `-q` per indicarli a mano). Lo zip di HSQLDB viene da SourceForge, che non pubblica sha256: il valore e' fissato nel Dockerfile in `HSQLDB_SHA256`, verificato contro lo SHA1 pubblicato, e va aggiornato insieme a `HSQLDB_FULLVERSION`.

