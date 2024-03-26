# govpay-batch-aca
Batch di alimentazione dell'ACA degli Avvisi pagoPA

## Istruzioni di compilazione

Il progetto utilizza librerie spring-boot versione 2.7.147 e JDK 11.

Per la compilazione eseguire il seguente comando, verranno eseguiti anche i test.


``` bash
mvn clean install -Denv=[localhost]
```

Il parametro env invece consente di valorizzare le properties per l'ambiente di installazione scelto (default: localhost).

Per l'avvio dell'applicativo come standalone eseguire:

``` bash
mvn spring-boot:run -Denv=[localhost]
```

# Configurazione

All'interno del file di filtro si possono definire le seguenti proprieta':

``` bash
# ------------ DIRECTORY LAVORO ESTERNA -----------

it.govpay.aca.batch.resource.path=[WORK_DIR]

# ------------ LOGGING -------------------

it.govpay.aca.batch.log.path=[LOG_DIR]
it.govpay.aca.batch.log.level=[LOG_LEVEL]

# ----------- SPRING SERVLET ------------

it.govpay.aca.batch.server.port=[Porta su cui esporre il servizio in caso di avvio come applicazione standalone]

# Abilitazione Endpoint /actuator/health/liveness
it.govpay.aca.batch.spring.actuator.path=[Basepath dove esporre i servizi di stato applicazione]

# ------------ HIBERNATE & JPA -------------------

# Configurazione DB
#it.govpay.aca.batch.spring.datasource.jndiName=[JNDI NAME del datasource]
it.govpay.aca.batch.spring.datasource.url=[URL CONNESSIONE DB]
it.govpay.aca.batch.spring.datasource.driverClassName=[CLASSE DRIVER JDBC]
it.govpay.aca.batch.spring.datasource.username=[USERNAME DB]
it.govpay.aca.batch.spring.datasource.password=[PASSWORD DB]

it.govpay.aca.batch.spring.jpa.database-platform=[DIALECT JPA]

it.govpay.aca.batch.spring.jpa.hibernate.ddl-auto=[Configura il comportamento di Hibernate nella generazione dello schema del database.]

# -------------- BUSINESS LOGIC PROPERTIES  ----------------

# Informazioni per la connessione verso PagoPA
it.govpay.aca.batch.client.header.subscriptionKey.name=[NOME HEADER SUBSCRIPTION-KEY]
it.govpay.aca.batch.client.header.subscriptionKey.value=[VALORE SUBSCRIPTION-KEY]
it.govpay.aca.batch.client.debugging=[DEBUG CHIAMATE VERSO IL SERVIZIO]
it.govpay.aca.batch.client.baseUrl=[BASE URL SERVIZIO ACA PAGOPA]

# Dimensione del chunk di lavoro
it.govpay.aca.batch.jobs.acaSenderJob.steps.spedizionePendenzaStep.chunk-size=[DIMENSIONE CHUNK]

# Limit da impostare nella query di ricerca sul DB
it.govpay.aca.batch.dbreader.numeroPendenze.limit=[LIMIT PER LA RICERCA SUL DB]

# Numero di giorni su cui limitare la ricerca delle pendenze da spedire all'ACA
it.govpay.aca.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni=[LIMITE TEMPORALE RICERCA PENDENZE DA SPEDIRE]
```
