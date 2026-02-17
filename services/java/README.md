# Java Services Skeleton

## Services
- auth-service: `18081`
- user-service: `18082`
- project-service: `18083`

## Start One Service
```bash
cd services/java/auth-service
./mvnw spring-boot:run
```

## Environment Variables
- `DB_URL` (default: `jdbc:postgresql://localhost:5432/<service_db>`)
- `DB_USER` (default: `postgres`)
- `DB_PASSWORD` (default: `postgres`)

## Health Endpoints
- App health: `/api/v1/health`
- Actuator health: `/actuator/health`

## Compile Check
```bash
cd services/java/auth-service && ./mvnw -DskipTests compile
cd services/java/user-service && ./mvnw -DskipTests compile
cd services/java/project-service && ./mvnw -DskipTests compile
```
