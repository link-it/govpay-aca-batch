# govpay-batch-aca
Batch di alimentazione del GPD degli Avvisi pagoPA

## Istruzioni di compilazione

Il progetto utilizza librerie spring-boot versione 2.7.147 e JDK 11.

Per la compilazione eseguire il seguente comando, verranno eseguiti anche i test.


``` bash
mvn clean install -P [jar|war]
```

Il profilo permette di selezionare il packaging dei progetti (jar o war).

Per l'avvio dell'applicativo come standalone eseguire:

``` bash
mvn spring-boot:run
```

Per sovrascrivere le proprieta' definite nel file `application.properties` utilizzare il seguente sistema:

``` bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.datasource.url=[NUOVO_VALORE] ..."

```

# Configurazione

All'interno del file `application.properties` sono definite le seguenti proprieta':

``` bash

# ----------- SPRING SERVLET ------------

server.port=[Porta su cui esporre il servizio in caso di avvio come applicazione standalone]

# Abilitazione Endpoint /actuator/health/liveness
management.endpoints.web.base-path=[Basepath dove esporre i servizi di stato applicazione]

# ------------ HIBERNATE & JPA -------------------

# Configurazione DB
#spring.datasource.jndiName=[JNDI NAME del datasource]
spring.datasource.url=[URL CONNESSIONE DB]
spring.datasource.driverClassName=[CLASSE DRIVER JDBC]
spring.datasource.username=[USERNAME DB]
spring.datasource.password=[PASSWORD DB]

spring.jpa.database-platform=[DIALECT JPA]
spring.jpa.properties.hibernate.dialect=[DIALECT JPA]

spring.jpa.hibernate.ddl-auto=[Configura il comportamento di Hibernate nella generazione dello schema del database.]

# -------------- BUSINESS LOGIC PROPERTIES  ----------------

# Informazioni per la connessione verso PagoPA
it.govpay.gpd.batch.client.header.subscriptionKey.name=[NOME HEADER SUBSCRIPTION-KEY]
it.govpay.gpd.batch.client.header.subscriptionKey.value=[VALORE SUBSCRIPTION-KEY]
it.govpay.gpd.batch.client.debugging=[DEBUG CHIAMATE VERSO IL SERVIZIO]
it.govpay.gpd.batch.client.baseUrl=[BASE URL SERVIZIO ACA PAGOPA]

# Indica se alimentare anche l'aca
it.govpay.gpd.aca.enabled=[TRUE|FALSE]

#Indica se la pendenza e' disponibile per la funzionalita' di standin
it.govpay.gpd.standIn.enabled=[TRUE|FALSE]
 
#Indica se tentare la pubblicazione direttamente senza passare dallo stato draft (valido solo se la pendenza non ha data scadenza)
it.govpay.gpd.toPublish.enabled=[TRUE|FALSE]

# Dimensione del chunk di lavoro
it.govpay.gpd.batch.jobs.gpdSenderJob.steps.spedizionePendenzaStep.chunk-size=[DIMENSIONE CHUNK]

# Limit da impostare nella query di ricerca sul DB
it.govpay.gpd.batch.dbreader.numeroPendenze.limit=[LIMIT PER LA RICERCA SUL DB]

# Numero di giorni su cui limitare la ricerca delle pendenze da spedire all'ACA
it.govpay.gpd.batch.dbreader.sogliaTemporaleRicercaPendenze.numeroGiorni=[LIMITE TEMPORALE RICERCA PENDENZE DA SPEDIRE]

# Configurazione GDE

# Abilita il servizio GDE
it.govpay.gde.enabled=[TRUE|FALSE]

# Base URL del servizio GDE
it.govpay.gde.client.baseUrl=[BASE URL SERVIZIO GDE]
```
