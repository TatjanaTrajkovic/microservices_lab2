# Laboration 2 — Distribuerad Chatt-Applikation (VG)

En mikrotjänstarkitektur med JWT-autentisering, gRPC-kommunikation, RabbitMQ event-bus och en event-driven BotService. Systemet körs antingen via Docker Compose (lokal utveckling) eller i ett Kubernetes-kluster med Minikube (VG-krav).

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
         │ REST               REST
         ├──────────────────────────────────────────┐
         v                                          v
  ┌─────────────┐                        ┌──────────────────┐
  │ AuthService │  (port 9000)           │  MessageService  │ (port 8082)
  │  JWT-utfärd │                        │  publicerar event│
  └─────────────┘                        └────────┬─────────┘
                                                  │ gRPC
                                                  v
                                         ┌──────────────────┐
                                         │   UserService    │ (port 8083)
                                         │   gRPC-server    │
                                         └────────┬─────────┘
                                                  │ JPA
                                                  v
                                         ┌──────────────────┐
                                         │     MySQL        │
                                         │  user_db         │
                                         │  message_db      │
                                         └──────────────────┘

  MessageService
        │ publish (message-published)
        v
  ┌─────────────┐
  │  RabbitMQ   │  chat.exchange
  └──────┬──────┘
         │ consume (bot.queue)
         v
  ┌─────────────┐
  │ BotService  │  ← analyserar meddelanden, svarar via REST
  └─────────────┘
```

### Tjänster

| Tjänst             | Port  | Ansvar                                                              |
|--------------------|-------|---------------------------------------------------------------------|
| **bff**            | 8080  | API-gateway, JWT-skydd, proxying till övriga tjänster               |
| **authservice**    | 9000  | Registrering, inloggning, JWT-utfärdning och verifiering            |
| **userservice**    | 8083  | Användarprofiler (REST + gRPC-server)                               |
| **messageservice** | 8082  | Meddelanden, publicerar händelser till RabbitMQ                     |
| **botservice**     | —     | Konsumerar events från RabbitMQ, svarar automatiskt via MessageService |

---

## Förutsättningar

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installerat och igång
- För Kubernetes: [Minikube](https://minikube.sigs.k8s.io/) installerat

---

## Starta med Docker Compose (lokal utveckling)

```bash
docker compose up --build
```

> Första gången tar det ~2–3 minuter. MySQL och RabbitMQ startar med health checks.

Kontrollera att allt är igång:
```bash
docker compose ps
```

Alla sju containers ska ha status `Up`: mysql, rabbitmq, authservice, userservice, messageservice, botservice, bff.

Stoppa:
```bash
docker compose down        # stoppa
docker compose down -v     # stoppa + radera databasdata
```

---

## Starta med Kubernetes / Minikube (VG)

### 1. Starta Minikube med tillräckliga resurser
```bash
minikube start --driver=docker --memory=6144 --cpus=4
```

### 2. Bygg images lokalt
```bash
docker compose build
```

> Kör INTE `eval $(minikube docker-env)` före bygget — det belastar Minikubes resurser och kan krascha apiservern.

### 3. Ladda images in i Minikube
```bash
minikube image load microservices_lab2-authservice:latest
minikube image load microservices_lab2-bff:latest
minikube image load microservices_lab2-botservice:latest
minikube image load microservices_lab2-messageservice:latest
minikube image load microservices_lab2-userservice:latest
```

### 4. Applicera alla Kubernetes-manifest
```bash
kubectl apply -f k8s/
```

### 5. Kontrollera att alla pods startar
```bash
kubectl get pods -w
```

> `userservice` och `messageservice` kan krascha ett par gånger tills MySQL är redo — det är normalt. Kubernetes startar om dem automatiskt. Vänta tills alla visar `Running`.

### 6. Hämta BFF-URL (öppna en ny terminal)
```bash
minikube service bff --url
```

Lämna den terminalen öppen — stänger du den försvinner tunneln. Notera URL:en, t.ex. `http://127.0.0.1:50847`. Porten är slumpmässig varje gång.

### Stoppa Kubernetes
```bash
kubectl delete -f k8s/
minikube stop
```

---

## Steg-för-steg demonstration

> Byt ut `http://localhost:8080` mot Minikube-URL:en om du kör i Kubernetes.

### 1. Registrera en användare

```bash
curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "email": "alice@example.com", "password": "secret123"}'
```

### 2. Logga in och spara JWT-token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "secret123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

echo "Token: $TOKEN"
```

### 3. Skicka ett meddelande

```bash
curl -s -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content": "Hej från mikrotjänstvärlden!"}'
```

**Vad som händer:**
1. BFF extraherar `userId` från JWT och skickar till MessageService
2. MessageService anropar UserService via **gRPC** för att hämta `senderUsername`
3. Meddelandet sparas i MySQL (`message_db`)
4. Ett `message-published`-event publiceras till **RabbitMQ**
5. BotService konsumerar eventet från `bot.queue`

### 4. Testa BotService

Skicka ett bot-kommando:
```bash
curl -s -X POST http://localhost:8080/api/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content": "!bot hej"}'
```

Vänta 1–2 sekunder, hämta sedan meddelanden:
```bash
curl -s http://localhost:8080/api/messages \
  -H "Authorization: Bearer $TOKEN"
```

Bot-svaret visas med `senderUsername: "bot"`.

**Tillgängliga bot-kommandon:**

| Kommando      | Bot svarar                                        |
|---------------|---------------------------------------------------|
| `!bot hej`    | Hälsning + info om kommandon                      |
| `!bot hjälp`  | Lista på alla kommandon                           |
| `!bot tid`    | Aktuell tid                                       |
| `!bot skämt`  | Ett slumpmässigt skämt                            |

### 5. Hämta alla meddelanden

```bash
curl -s http://localhost:8080/api/messages \
  -H "Authorization: Bearer $TOKEN"
```

---

## Arkitekturförklaring

### JWT-autentisering
- **AuthService** utfärdar tokens signerade med HS256 (JJWT 0.12.6)
- **BFF** validerar tokens i `JwtAuthFilter` och sätter `userId` i SecurityContext
- Lösenord hashas med BCrypt
- Token innehåller: `sub` (userId), `username`, `iat`, `exp`

### gRPC-kommunikation
- **UserService** exponerar en gRPC-server
- Proto: `GetUserProfileRequest { userId }` → `UserProfileResponse { userId, username, email, found }`
- **MessageService** är gRPC-klient och hämtar `senderUsername` vid varje nytt meddelande

### RabbitMQ — Event Bus
- Exchange: `chat.exchange` (Topic Exchange)
- Routing key: `message-published`
- `message.published` — befintlig kö
- `bot.queue` — BotService egna kö, bunden till samma exchange

### BotService — Event-driven
- Lyssnar på `bot.queue` via `@RabbitListener`
- Ignorerar meddelanden med `senderId: "bot"` för att undvika oändlig loop
- Svarar via `POST /api/messages` till MessageService
- Har ingen egen databas eller REST-API

### Kubernetes
- Alla tjänster kommunicerar via Kubernetes **Service DNS-namn** (t.ex. `http://messageservice:8082`)
- BFF exponeras som **NodePort** (port 30080) — enda externa ingångspunkten
- Övriga tjänster är **ClusterIP** — enbart åtkomliga inuti klustret
- MySQL och RabbitMQ körs som **StatefulSet** istället för Deployment eftersom de behöver stabil pod-identitet och persistent storage
- Varje StatefulSet använder en **Headless Service** (`clusterIP: None`) vilket ger varje pod ett stabilt DNS-namn (t.ex. `mysql-0.mysql`, `rabbitmq-0.rabbitmq`)
- Persistent storage hanteras via **`volumeClaimTemplates`** i StatefulSet-deklarationen — Kubernetes skapar automatiskt en PVC per pod med stabila namn (`data-mysql-0`, `data-rabbitmq-0`). Om en pod kraschar och startas om får den tillbaka exakt samma disk med samma data

---

## Felsökning

### Docker Compose
```bash
docker compose logs <tjänstnamn>
docker compose restart userservice messageservice
```

### Kubernetes
```bash
kubectl logs <pod-namn>
kubectl describe pod <pod-namn>
kubectl get pods
```

**Pods i CrashLoopBackOff?**
Troligen väntar tjänsten på MySQL eller RabbitMQ. Vänta 30 sekunder — Kubernetes startar om automatiskt tills allt är redo.

**Port redan används (Docker Compose)?**
```bash
lsof -ti:8080 | xargs kill -9
```