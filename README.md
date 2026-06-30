# Super Pont

Romanian football prediction website monorepo.

## Stack

- Frontend: React, TypeScript, Vite
- Backend: Java 21, Spring Boot, Gradle
- Database: PostgreSQL
- Local development: Docker Compose

## Project Structure

```text
frontend/
backend/
docker-compose.yml
README.md
```

## Prerequisites

- Docker and Docker Compose
- Node.js 20+ for running the frontend outside Docker
- Java 21 for running the backend outside Docker

## Local Development With Docker Compose

Start the full local stack:

```bash
docker compose up --build
```

Services:

- Frontend: http://localhost:5173
- Frontend health file: http://localhost:5173/health.json
- Backend: http://localhost:8080
- Backend health endpoint: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5432

Stop the stack:

```bash
docker compose down
```

Remove the local database volume:

```bash
docker compose down -v
```

## Run Frontend Locally

```bash
cd frontend
npm install
npm run dev
```

## Run Backend Locally

Start PostgreSQL first:

```bash
docker compose up db
```

Then run the backend:

```bash
cd backend
./gradlew bootRun
```

On Windows PowerShell:

```powershell
cd backend
.\gradlew.bat bootRun
```

## Build Backend

```bash
cd backend
./gradlew build
```

On Windows PowerShell:

```powershell
cd backend
.\gradlew.bat build
```

## Current Scope

This repository currently contains only initialized projects and health-check endpoints.
Authentication and football prediction domain entities are intentionally not implemented yet.
