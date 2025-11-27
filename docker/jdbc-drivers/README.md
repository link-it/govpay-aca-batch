# JDBC Drivers

Place JDBC driver JAR files in this directory. They will be mounted into the container at `/opt/jdbc-drivers`.

## Required Drivers

Download the JDBC driver for your database:

### PostgreSQL

```bash
wget https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar
```

**License**: BSD-2-Clause

### MySQL/MariaDB

```bash
wget https://repo1.maven.org/maven2/org/mariadb/jdbc/mariadb-java-client/3.3.3/mariadb-java-client-3.3.3.jar
```

**License**: LGPL-2.1

### Oracle

```bash
wget https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.3.0.23.09/ojdbc11-23.3.0.23.09.jar
```

**License**: Oracle Free Use Terms and Conditions (FUTC)
**Note**: Review Oracle's license terms before use in production

## Verification

After downloading, verify the driver is present:

```bash
ls -lh
```

You should see at least one `.jar` file.

## Multiple Databases

If you need to support multiple database types, you can place multiple drivers in this directory. The application will load the appropriate one based on the `GOVPAY_DB_TYPE` environment variable.

Example:
```
jdbc-drivers/
├── postgresql-42.7.3.jar
├── mariadb-java-client-3.3.3.jar
└── ojdbc11-23.3.0.23.09.jar
```

## Troubleshooting

If you see an error like "No suitable driver found", check:

1. The JAR file is in this directory
2. The JAR file has correct permissions (readable)
3. The `GOVPAY_DB_TYPE` matches your database
4. The driver class name is correct for your database type

## Alternative: Use .gitignore

If committing to version control, add this to `.gitignore`:

```
jdbc-drivers/*.jar
!jdbc-drivers/README.md
```

This prevents committing large driver files while keeping instructions.
