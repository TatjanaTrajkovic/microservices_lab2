# Laboration 2 — Distribuerad Chatt-Applikation

En mikrotjänstarkitektur med JWT-autentisering, gRPC-kommunikation och RabbitMQ-meddelandehantering.

---

## Arkitektur

```
  Klient (browser/curl)
        |
        | HTTP (port 8080)
        v
  ┌─────────────┐
  │     BFF     │  ← JWT-validering, proxy-lager
  └──────┬──────┘
         │ REST (HTTP)          REST (HTTP)
         ├──────────────────────────────────────────┐
         v                                          v
  ┌─────────────┐                        ┌──────────────────┐
  │ AuthService │  (port 9000)           │  MessageService  │ (port 8082)
  │  JWT-utfärd │                        │  RabbitMQ-publik.│
  └─────────────┘                        └────────┬─────────┘
         |                                        │ gRPC (port 8083)
         v                                        v
  ┌─────────────┐                        ┌──────────────────┐
  │ UserService │ ◄──────────────────────┤  (GetUserProfile)│
  │ gRPC-server │  (port 8083)           └──────────────────┘
  └──────┬──────┘
         │ JPA
         v
  ┌─────────────┐     ┌────────────┐
  │    MySQL    │     │  RabbitMQ  │
  │  user_db   │     │ (port 5672)│
  │ message_db │     └────────────┘
  └─────────────┘
```

### Tjänster

| Tjänst          | Port | Ansvar                                                    |
|-----------------|------|-----------------------------------------------------------|
| **authservice** | 9000 | Registrering, inloggning, JWT-utfärdning och verifiering |
| **userservice** | 8083 | Användarprofiler (REST + gRPC-server)                     |
| **messageservice** | 8082 | Meddelanden, publicerar händelser till RabbitMQ        |
| **bff**         | 8080 | API-gateway, JWT-skydd, proxying till övriga tjänster    |

---

## Förutsättningar

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installerat och igång
- Port 8080, 8082, 8083, 9000, 3306, 5672, 15672 lediga

---

## Starta applikationen

```bash
docker compose up --build
```

> **Första gången** tar det ~2–3 minuter. MySQL och RabbitMQ startar med health checks — de övriga tjänsterna startar automatiskt när infrastrukturen är redo.

Kontrollera att allt är igång:
```bash
docker compose ps
```

Alla sex containers (mysql, rabbitmq, authservice, userservice, messageservice, bff) ska ha status `Up`.

---

## Steg-för-steg demonstration (G-krav)

### 1. Registrera en användare

Registreringen skapar **både** en användarprofil i UserService och autentiseringsuppgifter i AuthService.

```bash
curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice@example.com", "password": "secret123"}' \
  | jq .
```

Förväntat svar:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "alice",
  "email": "alice@example.com"
}
```

> BFF anropar först UserService (skapar profil) och sedan AuthService (registrerar lösenord). Allt i ett enda API-anrop.

---

### 2. Logga in och hämta JWT-token

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' \
  | jq .
```

Förväntat svar:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

Spara token i en variabel för kommande anrop:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' \
  | jq -r '.token')

echo "Token: $TOKEN"
```

> JWT-token är signerad med HS256 och är giltig i 24 timmar. Den innehåller `userId` och `username` som claims.

---

### 3. Verifiera JWT-token (AuthService)

```bash
curl -s http://localhost:9000/api/auth/verify \
  -H "Authorization: Bearer $TOKEN" \
  | jq .
```

Förväntat svar:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "username": "alice"
}
```

> Denna endpoint visar att AuthService kan validera tokens oberoende av BFF — användbart för service-to-service-autentisering.

---

### 4. Hämta användarprofil (kräver JWT)

```bash
curl -s http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN" \
  | jq .
```

> BFF validerar JWT-token i JwtAuthFilter innan requesten proxyas till UserService. Utan giltig token returneras HTTP 401.

---

### 5. Skicka ett meddelande — gRPC + RabbitMQ

```bash
curl -s -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content": "Hej från mikrotjänstvärlden!"}' \
  | jq .
```

Förväntat svar:
```json
{
  "id": "a1b2c3d4-...",
  "content": "Hej från mikrotjänstvärlden!",
  "senderId": "550e8400-...",
  "senderUsername": "alice",
  "timestamp": "2026-05-27T20:05:00"
}
```

**Vad som händer bakom kulisserna:**

1. BFF extraherar `userId` från JWT-token och skickar med requesten till MessageService
2. **MessageService anropar UserService via gRPC** (`GetUserProfile`) för att hämta `senderUsername`
3. Meddelandet sparas i MySQL (`message_db`)
4. En `message-published`-händelse publiceras till RabbitMQ (exchange: `chat.exchange`, routing key: `message-published`)

---

### 6. Hämta alla meddelanden

```bash
curl -s http://localhost:8080/api/messages \
  -H "Authorization: Bearer $TOKEN" \
  | jq .
```

---

### 7. Verifiera gRPC-anropet i loggarna

```bash
# Se att MessageService gör gRPC-anropet till UserService
docker compose logs messageservice | grep -i grpc

# Se att UserService tar emot gRPC-anropet
docker compose logs userservice | grep -i grpc
```

---

### 8. Verifiera RabbitMQ-händelser

Öppna RabbitMQ Management Console: [http://localhost:15672](http://localhost:15672)

- **Användarnamn:** `guest`
- **Lösenord:** `guest`

Klicka på fliken **Queues** och hitta `message.published`. Under **Get messages** kan du inspektera publicerade händelser med fälten `messageId`, `content`, `senderId` och `senderUsername`.

---

## Arkitekturförklaring

### JWT-autentisering

- **AuthService** utfärdar tokens signerade med HS256 (JJWT 0.12.6)
- **BFF** validerar tokens i `JwtAuthFilter` (OncePerRequestFilter) och sätter `userId` i SecurityContext
- Lösenord hashas med BCrypt
- Token innehåller: `sub` (userId), `username`, `iat`, `exp`

### gRPC-kommunikation

- **UserService** exponerar en gRPC-server via `spring-grpc-server-web-spring-boot-starter`
- Proto-definition: `GetUserProfileRequest { userId }` → `UserProfileResponse { userId, username, email, found }`
- **MessageService** är gRPC-klient och anropar `GetUserProfile` när ett meddelande skickas

### RabbitMQ-meddelandehantering

- Exchange: `chat.exchange` (Topic Exchange)
- Routing key: `message-published`
- Queue: `message.published`
- Händelsen publiceras efter att meddelandet sparats i databasen

### Databaser

- `user_db` — användarprofiler (UserService)
- `message_db` — meddelanden (MessageService)
- Autentiseringsuppgifter lagras in-memory i H2 (AuthService, ingen persistent data behövs)

---

## Stopp och rensning

```bash
# Stoppa alla containers
docker compose down

# Stoppa och radera databasens data (börja om från scratch)
docker compose down -v
```

---

## Felsökning

**Tjänst startar inte?**
```bash
docker compose logs <tjänstnamn>
# Exempel:
docker compose logs userservice
```

**MySQL hinner inte starta?**
UserService och MessageService har `depends_on: mysql: condition: service_healthy`. Om MySQL ändå inte är redo, vänta 30 sekunder och kör:
```bash
docker compose restart userservice messageservice
```

**Port redan används?**
```bash
lsof -ti:8080 | xargs kill -9
```