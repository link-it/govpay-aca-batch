# GovPay A.C.A. (Avvisi Cortesia Automatici) - Docker Setup

Docker containerization for GovPay A.C.A. batch processor that handles automated courtesy notices for debt positions via pagoPA's GPD (Gestione Posizioni Debitorie) service.

## Overview

This Docker setup provides:
- Multi-database support (PostgreSQL, MySQL/MariaDB, Oracle)
- Flexible deployment modes (systemd daemon or cron one-shot)
- Integration with pagoPA GPD service
- Optional GDE (Giornale degli Eventi) integration
- Health checks and monitoring
- Configurable connection pooling

## Quick Start

### 1. Build the Docker Image

```bash
# Build with PostgreSQL support (default)
./build_image.sh

# Build with specific database
./build_image.sh -v 3.8.0 -d postgresql
./build_image.sh -v 3.8.0 -d mysql
./build_image.sh -v 3.8.0 -d oracle
```

### 2. Configure Environment

```bash
# Copy the template and edit
cp .env.template .env
nano .env
```

**Required configuration:**
- `DB_PASSWORD`: Database password
- `GPD_BASE_URL`: pagoPA GPD service URL
- `GPD_SUBSCRIPTION_KEY`: pagoPA API subscription key

### 3. Start the Services

```bash
# Start with docker-compose
docker-compose up -d

# View logs
docker-compose logs -f govpay-aca

# Check status
docker-compose ps
```

## Architecture

```
┌─────────────────────────────────────┐
│   GovPay A.C.A. Batch Processor    │
│                                     │
│  ┌──────────────────────────────┐  │
│  │   Spring Boot Application    │  │
│  │   - Spring Batch             │  │
│  │   - GPD Client               │  │
│  │   - GDE Client (optional)    │  │
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

## Deployment Modes

### Systemd Mode (Daemon)

Runs continuously with scheduled batch jobs:

```yaml
# In docker-compose.yml
command: ["systemd"]
```

Configuration:
- `ACTUATOR_PORT=8080`: Port for health checks
- `SCHEDULER_INTERVAL_MS=300000`: Execution interval (default: 5 minutes)

### Cron Mode (One-Shot)

Runs once and exits (for external cron scheduling):

```yaml
# In docker-compose.yml
command: ["cron"]
```

Example cron setup:
```bash
# Run every hour
0 * * * * docker-compose -f /path/to/docker-compose.yml run --rm govpay-aca cron
```

## Database Configuration

### PostgreSQL (Default)

```env
DB_JDBC_URL=jdbc:postgresql://postgres:5432/govpay
DB_DRIVER=org.postgresql.Driver
DB_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
```

### MySQL/MariaDB

```env
DB_JDBC_URL=jdbc:mysql://mysql:3306/govpay?zeroDateTimeBehavior=convertToNull
DB_DRIVER=com.mysql.cj.jdbc.Driver
DB_HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect
```

Edit `docker-compose.yml` to uncomment the MySQL service.

### Oracle

```env
DB_JDBC_URL=jdbc:oracle:thin:@//oracle:1521/XE
DB_DRIVER=oracle.jdbc.OracleDriver
DB_HIBERNATE_DIALECT=org.hibernate.dialect.OracleDialect
```

For TNS Names:
```env
DB_JDBC_URL=jdbc:oracle:thin:@TNSNAME
ORACLE_TNS_ADMIN=/etc/govpay
```

Mount `tnsnames.ora`:
```yaml
volumes:
  - ./tnsnames.ora:/etc/govpay/tnsnames.ora:ro
```

## Integration with GDE

To enable event logging via GDE:

```env
GDE_ENABLED=true
GDE_BASE_URL=http://govpay-gde-api:8080
```

Add to the same network in `docker-compose.yml`:
```yaml
networks:
  - govpay-aca-network
  - govpay-gde-network
```

## Configuration Reference

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | Yes | - | JDBC connection URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | - | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Yes | - | Database password |
| `GPD_BASE_URL` | Yes | - | pagoPA GPD service URL |
| `GPD_SUBSCRIPTION_KEY` | Yes | - | pagoPA API key |
| `GDE_ENABLED` | No | `false` | Enable GDE integration |
| `GDE_BASE_URL` | No | - | GDE service URL |
| `CLUSTER_ID` | No | hostname | Cluster identifier |
| `DB_POOL_MIN_IDLE` | No | `2` | Min idle connections |
| `DB_POOL_MAX_SIZE` | No | `10` | Max pool size |
| `ACTUATOR_PORT` | No | `8080` | Actuator port (systemd) |
| `SCHEDULER_INTERVAL_MS` | No | `300000` | Scheduler interval (systemd) |
| `JAVA_MIN_HEAP` | No | `256m` | Min heap size |
| `JAVA_MAX_HEAP` | No | `1024m` | Max heap size |
| `DEBUG` | No | `false` | Enable debug logging |

### Hardcoded Settings

These are baked into the Docker image:
- Timezone: `Europe/Rome`
- Chunk size: `10` records per batch
- Temporal threshold: `7` days for pending positions
- GPD subscription key header: `Ocp-Apim-Subscription-Key`
- Connection timeout: `20000ms`
- Idle timeout: `10000ms`

## Monitoring

### Health Check

Systemd mode exposes Spring Boot Actuator:

```bash
curl http://localhost:8080/actuator/health
```

### Logs

```bash
# Follow logs
docker-compose logs -f govpay-aca

# Inside container
docker exec -it govpay-aca-batch tail -f /var/log/govpay/govpay-aca.log
```

### Metrics

Access Spring Boot metrics:
```bash
curl http://localhost:8080/actuator/metrics
```

## Troubleshooting

### Database Connection Issues

```bash
# Check database is reachable
docker-compose exec govpay-aca ping postgres

# Verify credentials
docker-compose exec postgres psql -U govpay -d govpay -c "SELECT 1"
```

### GPD API Issues

```bash
# Test GPD connectivity
docker-compose exec govpay-aca curl -v ${GPD_BASE_URL}

# Check subscription key
docker-compose logs govpay-aca | grep "Subscription"
```

### Memory Issues

Adjust heap sizes in `.env`:
```env
JAVA_MIN_HEAP=512m
JAVA_MAX_HEAP=2048m
```

## File Structure

```
govpay-aca-batch/
├── Dockerfile.github       # Docker image definition
├── build_image.sh          # Build script
├── entrypoint.sh          # Container startup script
├── docker-compose.yml     # Orchestration configuration
├── .env.template          # Environment variables template
├── DOCKER.md             # This file
└── README.md             # Java development documentation
```

## pagoPA Environments

### Production
```env
GPD_BASE_URL=https://api.platform.pagopa.it/gpd/api/v1
```

### Collaudo (UAT)
```env
GPD_BASE_URL=https://api.uat.platform.pagopa.it/gpd/api/v1
```

## Security Considerations

1. **Credentials**: Never commit `.env` file to version control
2. **Secrets**: Use Docker secrets in production
3. **Network**: Use Docker networks to isolate services
4. **Firewall**: Restrict access to actuator endpoints
5. **SSL/TLS**: Use HTTPS for GPD and GDE connections
6. **Keys**: Rotate GPD subscription keys regularly

## Support

For issues related to:
- **GovPay**: https://github.com/link-it/govpay
- **pagoPA GPD**: https://docs.pagopa.it/
- **Java development**: See `README.md`

## License

This Docker setup follows the same license as GovPay.
