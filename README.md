# Origin Raid Planner

Origin Raid Planner is a full-stack raid planning application built for World of Warcraft guild officers. It centralizes roster management, raid signups, composition building, Discord publication, and weekly operational follow-up in a single workflow-oriented tool.

The project is designed around real officer constraints: balancing multiple raids across a week, handling mains and rerolls, publishing actionable information to Discord, and reducing spreadsheet-driven coordination.

## Product Overview

Origin Raid Planner covers the full lifecycle of raid preparation:

- Discord OAuth login restricted to guild officers
- guild member synchronization from Discord
- roster management with mains, rerolls, classes, specs, and roles
- weekly raid templates and ad hoc manual raids
- visual drag-and-drop composition building
- weekly auto-compose rules and officer validation tools
- Discord signup messages with interactive status updates
- reminders for missing signups
- publication of final compositions back to Discord
- confirmation tracking, bench management, and fairness monitoring

## Why This Project Is Strong Portfolio Material

This is not a generic CRUD dashboard. It is a production-oriented product with non-trivial business logic and external system integration.

It demonstrates:

- end-to-end product design for a real user group
- Spring Boot API design around domain-heavy workflows
- Angular UI built for operational decision-making
- Discord bot and OAuth integration
- scheduling, reminders, and automated weekly publishing
- Docker-based deployment and maintenance scripts

## Architecture

The project is split into two applications:

- **Backend**: Spring Boot API at the repository root
- **Frontend**: Angular application in [`raid-planner-ui/`](./raid-planner-ui)

### Backend responsibilities

- authentication and access control
- roster and raid domain logic
- weekly template generation
- reminder and publication workflows
- Discord API integration via JDA
- persistence, migrations, and operational endpoints

### Frontend responsibilities

- officer dashboard and weekly planning views
- composition building and validation workflows
- template administration
- publication and reminder tooling
- compact comparison views for cross-raid decision making

## Technical Stack

### Backend

- Java 15
- Spring Boot 2.4
- Spring Web
- Spring Data JPA
- MySQL 8
- Flyway
- JDA (Discord API)

### Frontend

- Angular 19
- TypeScript
- SCSS
- Angular CDK
- SortableJS

### Infrastructure

- Docker
- Docker Compose
- Nginx
- VPS deployment scripts
- MySQL backup and restore scripts

## Key Workflows

### Officer workflow

- navigate raids on a `Wednesday -> Tuesday` planning cycle
- create weekly or one-off raids
- build 2-group compositions visually
- lock, validate, and publish raid compositions
- compare a raid against a reference raid already prepared
- monitor confirmations, absences, bench, and signup gaps

### Discord workflow

- publish signup messages to the correct guild channel
- let players update their status directly from Discord
- send reminder messages when signups are missing
- publish final compositions with officer-ready formatting

## Repository Structure

- [`src/main/java/com/origin/`](./src/main/java/com/origin): backend source code
- [`src/main/resources/`](./src/main/resources): application configuration and resources
- [`src/main/resources/db/migration/`](./src/main/resources/db/migration): Flyway migrations
- [`raid-planner-ui/src/`](./raid-planner-ui/src): frontend source code
- [`docker-compose.prod.yml`](./docker-compose.prod.yml): production stack definition
- [`scripts/`](./scripts): deployment, backup, and restore scripts

## Local Setup

### Prerequisites

- Java 15+
- Maven
- Node.js + npm
- MySQL 8
- a configured Discord application and bot

### Backend

Useful environment variables:

- `DISCORD_BOT_TOKEN`
- `DISCORD_GUILD_ID`
- `DISCORD_OAUTH_CLIENT_ID`
- `DISCORD_OAUTH_CLIENT_SECRET`
- `DISCORD_OAUTH_REDIRECT_URI`
- `DISCORD_OAUTH_FRONTEND_SUCCESS_URL`
- `DISCORD_OAUTH_FRONTEND_DENIED_URL`

Run locally:

```powershell
$env:DISCORD_BOT_TOKEN="..."
$env:DISCORD_OAUTH_CLIENT_ID="..."
$env:DISCORD_OAUTH_CLIENT_SECRET="..."
mvn spring-boot:run
```

### Frontend

```powershell
cd raid-planner-ui
npm install
npm start
```

Default local URLs:

- frontend: [http://localhost:4200](http://localhost:4200)
- backend: [http://localhost:8080](http://localhost:8080)

## Production Deployment

1. Copy [`.env.prod.example`](./.env.prod.example) to `.env.prod`
2. Fill in Discord and MySQL secrets
3. Start the production stack:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

Useful operational scripts:

- [`scripts/deploy-frontend.sh`](./scripts/deploy-frontend.sh)
- [`scripts/deploy-backend.sh`](./scripts/deploy-backend.sh)
- [`scripts/deploy-all.sh`](./scripts/deploy-all.sh)
- [`scripts/backup-db.sh`](./scripts/backup-db.sh)
- [`scripts/restore-db.sh`](./scripts/restore-db.sh)

## Git Workflow Note

The project currently uses two Git repositories:

- the root repository for backend, infrastructure, deployment scripts, and the frontend pointer
- a separate frontend repository in [`raid-planner-ui/`](./raid-planner-ui)

When a change affects the frontend:

1. commit and push inside `raid-planner-ui`
2. commit and push the root repository to update the frontend pointer

## Current Direction

The application is already usable in production for officer workflows. The most natural next steps are:

- stronger automated test coverage
- more observability and operational monitoring
- continued UX refinement for officer decision-making
- additional hardening around production automation
