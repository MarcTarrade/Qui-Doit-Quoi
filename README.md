# Qui doit quoi ?

**Qui doit quoi ?** est une application web open source pour organiser et équilibrer les dépenses partagées. Elle s’adresse aux colocataires, aux groupes d’amis et à toute personne qui partage des frais.

Créez un groupe, ajoutez ses membres et enregistrez les dépenses. L’application calcule les soldes de chacun et indique les remboursements à effectuer pour équilibrer les comptes.

## Essayer l’application

L’application est accessible en ligne sur **[quidoitquoi.duckdns.org](https://quidoitquoi.duckdns.org)**. Vous pouvez l’utiliser directement, ou choisir de l’installer et de l’exécuter chez vous avec Docker Compose en suivant les étapes ci-dessous.

## Ce que l’application permet de faire

- Créer un compte et se connecter.
- Créer des groupes avec une devise et une description.
- Ajouter et gérer les membres d’un groupe.
- Enregistrer des dépenses et préciser qui les a payées.
- Consulter l’historique des dépenses, les soldes et les remboursements suggérés.

## Installation rapide

### Prérequis

Installez [Docker Desktop](https://www.docker.com/products/docker-desktop/) (ou Docker Engine avec Docker Compose).

### 1. Récupérer le projet

```bash
git clone https://github.com/MarcTarrade/Qui-Doit-Quoi.git
cd Qui-Doit-Quoi
```

### 2. Configurer et démarrer

Créez un fichier `.env` à la racine du projet :

```dotenv
POSTGRES_DB=quidoitquoi
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

Puis lancez l’application :

```bash
docker compose up --build
```

L’application web est disponible sur <http://localhost:3001>. L’API est sur <http://localhost:8080> et sa documentation interactive sur <http://localhost:8080/swagger-ui/index.html>.

Pour arrêter les services, utilisez `Ctrl+C` ou exécutez `docker compose down` dans un autre terminal.

## Technologies

| Partie | Technologies |
| --- | --- |
| Interface web | Next.js, React, TypeScript, Tailwind CSS |
| API | Java 25, Spring Boot, Spring Security, Spring Data JPA |
| Base de données | PostgreSQL 16 |
| Déploiement local | Docker Compose |

## Organisation du dépôt

```text
.
├── api/                 # API Spring Boot et tests
├── frontend/            # Application Next.js et tests de parcours
└── docker-compose.yaml  # Configuration des services locaux
```

## Développement et sécurité

Le projet peut aussi être lancé composant par composant : le frontend requiert Node.js 24 et npm ; l’API requiert Java 25 et PostgreSQL 16. Les fichiers de configuration et les commandes de tests se trouvent dans `frontend/` et `api/`.

Les valeurs d’identifiants données dans ce guide sont réservées au développement local. Avant tout déploiement, configurez des mots de passe et secrets robustes, activez les cookies sécurisés et définissez les origines CORS adaptées à votre environnement. Le profil de développement de l’API ne doit pas être utilisé en production.