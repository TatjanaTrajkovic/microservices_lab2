**Laboration 2 – Microservices och distribuerade system**

Distribuerad chattapplikation med BFF, microservices, gRPC och event-driven arkitektur
I denna laboration ska du utveckla en distribuerad chattlösning uppdelad i flera oberoende
microservices.

Fokus ligger på:
• tjänsteisolering
• intern kommunikation via gRPC
• extern kommunikation via REST genom en BFF
• händelsedriven arkitektur med Message Queue
• JWT-baserad autentisering
• containerisering och (för VG) driftsättning i Kubernetes

**Arkitekturöversikt**
Systemet består av följande komponenter:

**1. BFF (Backend-for-Frontend)**
   • Exponerar REST-API mot klienter
   • Validerar JWT
   • Vidarebefordrar anrop till interna tjänster
   • Kan implementeras själv eller ersättas av t.ex. Kong API Gateway

**2. Auth Service**
   • Hanterar inloggning
   • Utfärdar JWT
   • Verifierar användaruppgifter

**3. User Service**
   • Hanterar användarprofiler
   • Lagrar användardata i egen databas
   • Kan ta emot gRPC-anrop från Message Service (t.ex. för att hämta användarinfo)

**4. Message Service**
   • Tar emot och lagrar meddelanden
   • Publicerar händelsen "message-published" till Message Queue
   • Kan anropa User Service via gRPC för att hämta användarprofil

**5. Message Queue (Event Bus)**
   • Tar emot publicerade händelser från Message Service
   • Distribuerar händelser till konsumenter (t.ex. Bot Service)
   • Kan implementeras med RabbitMQ, Kafka eller NATS

**6. Bot Service (valfri)**
   • Konsumerar "message-published"
   • Kan analysera meddelanden och svara automatiskt
   • Publicerar svar genom att anropa Message Service (REST eller gRPC)

**7. Databaser**
   • UserDB (för User Service)
   • MessageDB (för Message Service)

   **Mål**

   **Godkänt (G)**
   För G ska du:

   • Implementera minst **tre tjänster**:**User Service, Message Service, Auth Service**

   • Implementera en **BFF** som exponerar REST-API

   • Använda **JWT-autentisering**

   • Låta tjänsterna kommunicera internt via **gRPC** eller **Message Queue**

   • Implementera publicering av händelsen **"message-published"** från Message Service

   • Implementera en enkel klient (t.ex. webbsida eller CLI) som använder BFF

   **Väl Godkänt (VG)**
   För VG ska du dessutom:
   • Paketera alla tjänster i **Docker-images**
   • Driftsätta hela systemet i ett **Kubernetes-kluster** (t.ex. Minikube)
   • Använda Kubernetes **Services** för intern kommunikation
   • Visa att tjänsterna kommunicerar via interna DNS-namn
   • (Valfritt men rekommenderat) Implementera **Bot Service** som konsumerar MQ-händelser

   **Funktionella krav**

   **BFF**
   • REST-endpoints för att skapa användare, logga in, skicka meddelanden och hämta
   meddelanden
   • Validerar JWT innan anrop vidarebefordras

   **Auth Service**
   • Endpoint för inloggning
   • Returnerar JWT med användar-ID

   **User Service**
   • CRUD för användare
   • Egen databas
   • gRPC-endpoint för att hämta användarprofil

   **Message Service**
   • Tar emot meddelanden via REST (från BFF)
   • Lagrar meddelanden i egen databas
   • Publicerar "message-published" till MQ
   • (Valfritt) gRPC-anrop till User Service för att hämta användarinfo

   **Bot Service (valfri)**
   • Konsumerar "message-published"
   • Kan generera automatiska svar
   • Publicerar svar via Message Service

   **Examination**
   Laborationen examineras genom:
   • kodgranskning
   • muntlig redovisning
   • demonstration av systemet i körning