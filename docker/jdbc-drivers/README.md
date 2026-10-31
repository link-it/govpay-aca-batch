# Driver JDBC

I driver JDBC non sono inclusi nell'immagine: si copiano in questa directory e si montano nel container in `/opt/jdbc-drivers` (o nella directory indicata da `GOVPAY_DS_JDBC_LIBS`). Le versioni sono quelle gestite da [govpay-bom](https://github.com/link-it/govpay-bom).

| Database (`GOVPAY_DB_TYPE`) | Driver | Licenza |
|-----------------------------|--------|---------|
| `postgresql` | [postgresql-42.7.14.jar](https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar) | BSD-2-Clause |
| `mysql`, `mariadb` | [mysql-connector-j-26.10.0.jar](https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/26.10.0/mysql-connector-j-26.10.0.jar) | GPL-2.0 con Universal FOSS Exception |
| `oracle` | [ojdbc11-23.26.3.0.0.jar](https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.26.3.0.0/ojdbc11-23.26.3.0.0.jar) | Oracle Free Use Terms and Conditions |

Anche con MariaDB il container usa il driver MySQL (`com.mysql.cj.jdbc.Driver`): il driver `mariadb-java-client` non viene usato.

Il driver serve sia al batch sia all'inizializzazione del database (`GOVPAY_ACA_POP_DB_SKIP=FALSE`). Se all'avvio compare "No suitable driver found", verificare che il jar sia nella directory montata, leggibile dall'utente del container, e che `GOVPAY_DB_TYPE` corrisponda al database.
