# RaidPlanner

RaidPlanner est un outil de pilotage de roster pour World of Warcraft.

Le projet couvre tout le flux officier :
- import automatique des raids Discord / Raid-Helper
- gestion des personnages, mains et rerolls
- composition manuelle avec drag-and-drop
- auto-compose hebdo
- publication Discord et mise a jour des compos
- rappels des non-inscrits
- dashboard officier, diagnostics et sante du planning
- authentification Discord reservee aux officiers

## Vue d'ensemble

Le projet est compose de deux parties :
- un backend Spring Boot a la racine du repo
- un frontend Angular dans `raid-planner-ui/`

Important :
- `raid-planner-ui/` est un depot Git imbrique avec son propre remote
- si tu modifies le front, il faut commit/push le front puis mettre a jour le pointeur Git du repo racine

## Fonctionnalites principales

### Officiers

- vue semaine `mercredi -> mardi`
- composition des groupes de raid
- verrouillage et statut de compo
- comparaison avec la derniere publication
- previsualisation et generation auto-compose
- bench manager
- suivi des confirmations
- dashboard global et alertes
- diagnostics de source Discord
- scheduler d'import configurable depuis l'admin
- templates de raid
- historique des publications

### Membres

- confirmations sur les compos publiees par le bot
- prototype d'inscriptions Discord maison sur salon de test

## Stack technique

### Backend

- Java 15
- Spring Boot 2.4.12
- Spring Web
- Spring Data JPA
- MySQL 8
- JDA 5 beta
- Flyway

### Frontend

- Angular 19
- TypeScript
- SCSS
- SortableJS

## Arborescence utile

- `src/main/java/com/origin/` : code backend
- `src/main/resources/application.properties` : config locale
- `src/main/resources/application-prod.properties` : config prod
- `src/main/resources/db/migration/` : migrations Flyway
- `raid-planner-ui/src/` : code frontend
- `docker-compose.prod.yml` : stack de deploiement
- `scripts/backup-db.sh` : backup MySQL
- `scripts/restore-db.sh` : restauration MySQL

## Demarrage local

### Prerequis

- Java 15+ installe
- Maven installe
- Node.js + npm installes
- MySQL accessible localement
- application Discord configuree

### Base de donnees

Creer une base MySQL nommee `origin`, ou adapter la variable `SPRING_DATASOURCE_URL`.

Par defaut en local :

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/origin?useSSL=false&serverTimezone=Europe/Paris
spring.datasource.username=root
spring.datasource.password=root
```

### Variables d'environnement utiles

#### Bot Discord

- `DISCORD_BOT_TOKEN`
- `DISCORD_GUILD_ID`

#### OAuth Discord

- `DISCORD_OAUTH_CLIENT_ID`
- `DISCORD_OAUTH_CLIENT_SECRET`
- `DISCORD_OAUTH_REDIRECT_URI`
- `DISCORD_OAUTH_FRONTEND_SUCCESS_URL`
- `DISCORD_OAUTH_FRONTEND_DENIED_URL`

#### Raid-Helper / salons

- `DISCORD_RAIDHELPER_CHANNEL_1`
- `DISCORD_RAIDHELPER_CHANNEL_2`
- `DISCORD_RAIDHELPER_CHANNEL_3`
- `DISCORD_RAIDHELPER_CHANNEL_4`
- `DISCORD_RAIDHELPER_DEFAULT_TIME`

### Lancer le backend

PowerShell :

```powershell
$env:DISCORD_BOT_TOKEN="ton_token_bot"
$env:DISCORD_OAUTH_CLIENT_ID="ton_client_id"
$env:DISCORD_OAUTH_CLIENT_SECRET="ton_client_secret"
mvn spring-boot:run
```

### Lancer le frontend

```powershell
cd raid-planner-ui
npm install
npm start
```

Frontend :
- [http://localhost:4200](http://localhost:4200)

Backend :
- [http://localhost:8080](http://localhost:8080)

## Authentification Discord

L'application web est reservee aux officiers.

Dans le Discord Developer Portal, il faut configurer :
- Redirect URI : `http://localhost:8080/api/auth/discord/callback` en local
- scopes OAuth : `identify` et `guilds.members.read`

Le backend verifie que l'utilisateur connecte possede le role `Officiers` sur le serveur Discord.

## Scheduler d'import

Le scheduler d'import Raid-Helper est maintenant pilotable depuis l'interface admin.

Il permet de choisir :
- activation / desactivation
- jour
- heure
- minute
- fuseau

Par defaut, l'import vise la publication des raids le jeudi a 21:00.

## Migrations et schema

Le projet supporte maintenant Flyway.

### En local

La configuration locale garde :

```properties
spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=false
```

### En production

Le profil `prod` active :
- `spring.jpa.hibernate.ddl-auto=validate`
- `spring.flyway.enabled=true`

La migration initiale est dans :
- `src/main/resources/db/migration/V1__initial_schema.sql`

Note :
- `baseline-on-migrate=true` permet d'adopter Flyway sur une base existante sans la casser

## Deploiement production

Le repo contient un pack de prod minimal et propre :
- backend conteneurise
- frontend Nginx
- MySQL
- healthchecks
- variables d'environnement

### Fichiers

- `docker-compose.prod.yml`
- `Dockerfile.backend`
- `raid-planner-ui/Dockerfile`
- `raid-planner-ui/docker/nginx/default.conf`
- `.env.prod.example`

### Mise en place

1. Copier `.env.prod.example` vers `.env.prod`
2. Remplir les secrets Discord et MySQL
3. Lancer :

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

## Sauvegarde et restauration

### Backup

```bash
chmod +x scripts/*.sh
./scripts/backup-db.sh
```

### Restore

```bash
./scripts/restore-db.sh backups/origin-YYYYMMDD-HHMMSS.sql.gz
```

## Commandes utiles

### Backend

Compiler sans tests :

```bash
mvn -DskipTests compiler:compile
```

Packager :

```bash
mvn -DskipTests package
```

### Frontend

Build production :

```bash
cd raid-planner-ui
npm run build
```

## Problemes courants

### `Token may not be empty`

Cause :
- `DISCORD_BOT_TOKEN` n'est pas defini

Fix :

```powershell
$env:DISCORD_BOT_TOKEN="ton_token_bot"
```

### L'auth Discord ne marche pas

Verifier :
- `DISCORD_OAUTH_CLIENT_ID`
- `DISCORD_OAUTH_CLIENT_SECRET`
- la Redirect URI dans le portail Discord
- le scope `guilds.members.read`

### Des messages Discord sont introuvables

Les anciens messages supprimes ou archives peuvent remonter comme `Unknown Message`.
Le backend degrade maintenant ce cas en log discret dans la plupart des flux.

## Etat du projet

Le projet est deja tres complet cote produit.

Les prochains chantiers les plus naturels sont plutot :
- fiabilite prod
- monitoring
- backups reguliers
- polish UX final
- durcissement des tests metier

## Git

Le projet utilise actuellement deux depots :
- repo racine : backend + orchestration + pointeur vers le front
- `raid-planner-ui/` : repo frontend dedie

Workflow recommande si le front change :
1. commit / push dans `raid-planner-ui/`
2. commit / push du repo racine pour mettre a jour le pointeur
