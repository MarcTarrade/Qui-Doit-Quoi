# Qui doit quoi ?

**Qui doit quoi ?** est une application web de gestion des dépenses partagées. Elle permet de créer des groupes, d’y ajouter des membres et des dépenses, puis de consulter les soldes et les remboursements nécessaires.

## Fonctionnalités

- Création de compte, connexion et déconnexion.
- Création et modification de groupes, avec devise et description.
- Gestion des membres d’un groupe.
- Ajout de dépenses et choix de leur catégorie et de la personne qui les a payées.
- Consultation des dépenses, des soldes et des remboursements à effectuer.

## Technologies

- **Frontend :** Next.js, React, TypeScript et CSS.
- **API :** Java 25, Spring Boot, Spring Security et Spring Data JPA.
- **Base de données :** PostgreSQL 16.
- **Conteneurs :** Docker et Docker Compose.

## Démarrage avec Docker Compose

### Prérequis

- Docker avec Docker Compose.

### Configuration

À la racine du projet, créez un fichier `.env` contenant les variables suivantes :

```dotenv
POSTGRES_DB=quidoitquoi
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Valeurs réservées au développement local : remplacez-les en production.
JWT_SECRET=change-this-development-secret-to-a-long-random-value
JWT_COOKIE_SECURE=false
CORS_ALLOWED_ORIGINS=http://localhost:3001
SPRING_PROFILES_ACTIVE=dev
NEXT_PUBLIC_API_URL=http://localhost:8080
```

Les valeurs ci-dessus sont des exemples de développement local, pas des secrets de production. En production, définissez un secret JWT long et aléatoire, activez les cookies sécurisés (`JWT_COOKIE_SECURE=true`) et configurez les origines autorisées et les identifiants de base de données adaptés à votre déploiement.

### Lancer l’application

Depuis la racine du dépôt :

```bash
docker compose up --build
```

Une fois les services démarrés :

- Application web : <http://localhost:3001>
- API : <http://localhost:8080>
- Documentation interactive de l’API : <http://localhost:8080/swagger-ui/index.html>


Les données PostgreSQL sont conservées dans le volume Docker `postgres_data`.

## Développement local

### Frontend

Prérequis : Node.js 24 et npm.

```bash
cd frontend
npm ci
```

Le fichier [`frontend/.env.example`](frontend/.env.example) contient l’URL locale de l’API attendue. Copiez-le vers `.env.local` si vous devez la modifier, puis démarrez le serveur :

```bash
npm run dev
```

Le frontend est disponible sur <http://localhost:3001>. Par défaut, il contacte l’API à l’adresse `http://localhost:8080`.

### API

Prérequis : Java 25 et PostgreSQL 16. La base doit être démarrée et les variables de connexion `POSTGRES_DB`, `POSTGRES_USER` et `POSTGRES_PASSWORD` doivent être définies dans l’environnement du processus Java. Le fichier `.env` à la racine est lu par Docker Compose, mais pas automatiquement par la commande Maven.

L’API utilise le wrapper Maven du dépôt. Depuis le dossier `api` :

```bash
# macOS / Linux
export POSTGRES_DB=quidoitquoi
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=postgres
export SPRING_DOCKER_COMPOSE_LIFECYCLE_MANAGEMENT=none
./mvnw spring-boot:run

# Windows PowerShell
$env:POSTGRES_DB = "quidoitquoi"
$env:POSTGRES_USER = "postgres"
$env:POSTGRES_PASSWORD = "postgres"
$env:SPRING_DOCKER_COMPOSE_LIFECYCLE_MANAGEMENT = "none"
.\mvnw.cmd spring-boot:run
```

La désactivation du cycle de vie Docker Compose évite que Spring ne lance aussi les autres services de la pile pendant le démarrage local de l’API. Le profil de développement est activé par défaut par la configuration Compose ; il crée un utilisateur de développement configurable par variables d’environnement et ne doit pas être utilisé en production.

## Tests et vérifications

### Frontend

Depuis le dossier `frontend` :

```bash
npm ci
npm run lint
npm run build
```

Les tests de parcours utilisent Playwright. Installez Chromium une première fois, puis lancez les tests :

```bash
npx playwright install chromium
npm run test:e2e
```

### API

Les tests de l’API nécessitent une base PostgreSQL disponible. La configuration de test utilisée par l’intégration continue désactive la gestion automatique de Docker Compose par Spring :

```bash
# macOS / Linux
SPRING_DOCKER_COMPOSE_LIFECYCLE_MANAGEMENT=none ./mvnw test

# Windows PowerShell
$env:SPRING_DOCKER_COMPOSE_LIFECYCLE_MANAGEMENT = "none"
.\mvnw.cmd test
```

## API

Les routes métier sont préfixées par `/api`. La documentation OpenAPI et l’interface Swagger sont disponibles à `/swagger-ui/index.html` lorsque l’API est lancée.

| Ressource | Routes principales |
| --- | --- |
| Authentification | `/api/auth/signup`, `/api/auth/login`, `/api/auth/logout`, `/api/auth/csrf` |
| Utilisateurs | `/api/users`, `/api/users/me` |
| Groupes | `GET /api/groups/user/{userId}`, `POST /api/groups/{userId}`, `/api/groups/{id}`, `/api/groups/{id}/settlements` |
| Membres | `/api/groups/{groupId}/members`, `/api/members/{id}` |
| Dépenses | `/api/groups/{groupId}/expenses`, `/api/expenses/{id}` |

Les opérations qui modifient les données sont protégées contre les requêtes CSRF. Les sessions authentifiées utilisent un cookie HTTP-only.

## Structure du dépôt

```text
.
├── api/                 # API Spring Boot, tests et Dockerfile
├── frontend/            # Application Next.js, tests E2E et Dockerfile
├── docker-compose.yaml  # Services PostgreSQL, API et frontend
└── README.md
```
