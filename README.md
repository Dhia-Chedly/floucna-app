# Floucna Mina Fina

Enhanced secure micro-lending portal for Project 15.

The project now follows `floucna_project15_enhanced_hld.md` as the single source of truth:
- Keycloak/OIDC authentication with BORROWER and ADMIN roles
- Didit KYC integration with local fallback
- Borrower loan submission + admin approval flow
- PDF generation, signing, timestamping, and compliance verification
- Audit logging for all critical actions

## Stack

- Frontend: Next.js (App Router), TypeScript
- Backend: Java 17, Javalin
- Database: SQLite
- PDF/Crypto: Apache PDFBox, BouncyCastle
- Compliance: DSS-oriented verification flow

## Run Locally

### Full stack with Docker Compose (includes persistent Keycloak)

```bash
docker compose up --build -d
```

Services:
- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- Keycloak: `http://localhost:8081`

Keycloak bootstrap:
- Admin console user: `admin`
- Admin console password: `admin123`
- Realm: `floucna` (auto-imported from `keycloak/realm-floucna.json`)
- Borrower self-signup: enabled (use `http://localhost:3000/register`)
- Test borrower user: `borrower` / `borrower123`
- Test admin user: `admin` / `admin123`

Persistence:
- Keycloak database is stored in docker volume `floucna-keycloak-db`.
- Backend SQLite data is stored in docker volume `floucna-db`.

If you need a clean reset:

```bash
docker compose down -v
docker compose up --build -d
```

### Use Didit KYC (provider mode)

1. Prepare env file:

```bash
cp .env.example .env
```

2. Fill all Didit variables in `.env`:
- `FLOUCNA_DIDIT_WORKFLOW_ID`
- `FLOUCNA_DIDIT_API_KEY`
- `FLOUCNA_DIDIT_WEBHOOK_SECRET`
- `NGROK_AUTHTOKEN`
- `NGROK_DOMAIN` (recommended, stable URL)

Optional:
- `FLOUCNA_DIDIT_WEBHOOK_URL` (override; otherwise auto-derived from `NGROK_DOMAIN`)

To list workflows and get `FLOUCNA_DIDIT_WORKFLOW_ID`:

```bash
curl --request GET \
  --url https://verification.didit.me/v3/workflows/ \
  --header "x-api-key: YOUR_API_KEY"
```

Use a KYC workflow `uuid` from the response.

3. Make sure `FLOUCNA_DIDIT_WEBHOOK_URL` is public and ends with:
- `/api/kyc/webhook/didit`

With the default compose setup:
- `ngrok` starts automatically.
- Webhook URL becomes `https://<NGROK_DOMAIN>/api/kyc/webhook/didit`.
- ngrok inspector is available at `http://localhost:4040`.
- If you do not reserve a domain, ngrok URL can change on restart and you must update Didit webhook settings.

4. Start services:

```bash
docker compose up -d --build
```

In this repository, compose is now configured for Didit-first KYC (`FLOUCNA_KYC_MODE=DIDIT`) with fallback disabled, so missing/invalid Didit config fails fast instead of silently switching to local upload.

### Backend

```bash
cd backend
chmod +x mvnw
./mvnw -DskipTests package
java -jar target/backend-1.0-SNAPSHOT.jar
```

Backend runs on `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:3000`.

## Auth Modes

Set backend and frontend auth mode via env.

### 1) Keycloak mode (recommended)

Backend:
- `FLOUCNA_AUTH_MODE=KEYCLOAK`
- `FLOUCNA_KEYCLOAK_INTROSPECTION_URL=...`
- `FLOUCNA_KEYCLOAK_CLIENT_ID=...`
- `FLOUCNA_KEYCLOAK_CLIENT_SECRET=...`

Frontend:
- `NEXT_PUBLIC_AUTH_MODE=KEYCLOAK`
- `NEXT_PUBLIC_KEYCLOAK_URL=...`
- `NEXT_PUBLIC_KEYCLOAK_REALM=...`
- `NEXT_PUBLIC_KEYCLOAK_CLIENT_ID=...`

### 2) Local token mode (dev fallback)

Backend:
- `FLOUCNA_AUTH_MODE=LOCAL`

Frontend:
- `NEXT_PUBLIC_AUTH_MODE=LOCAL`

In LOCAL mode, login page accepts a local bearer token payload (JWT-like structure with `sub`, `email`, optional `role` or `roles`).

## KYC Modes

- `FLOUCNA_KYC_MODE=DIDIT` for provider mode
- `FLOUCNA_KYC_MODE=LOCAL` for fallback mode
- `FLOUCNA_KYC_ALLOW_FALLBACK=true` to auto-fallback when provider fails

Didit envs (for DIDIT mode):
- `FLOUCNA_DIDIT_API_BASE_URL` (default `https://verification.didit.me`)
- `FLOUCNA_DIDIT_WORKFLOW_ID`
- `FLOUCNA_DIDIT_API_KEY`
- `FLOUCNA_DIDIT_WEBHOOK_SECRET`
- `NGROK_AUTHTOKEN`
- `NGROK_DOMAIN`
- `FLOUCNA_DIDIT_WEBHOOK_URL` (optional override)
- `FLOUCNA_DIDIT_CALLBACK_URL` (optional, default `http://localhost:3000/kyc`)

## Schema Reset

The app uses enhanced-schema tables only (`users`, `kyc_records`, `loan_requests`, `contracts`, `audit_logs`).

- `FLOUCNA_SCHEMA_RESET=true` (default) performs hard reset at startup.

## API Overview

### Auth
- `GET /api/me`

### KYC
- `POST /api/kyc/session`
- `GET /api/kyc/status`
- `POST /api/kyc/local/upload`
- `POST /api/kyc/webhook/didit`
- `GET /api/admin/kyc/pending`
- `POST /api/admin/kyc/{id}/approve`
- `POST /api/admin/kyc/{id}/reject`

### Loans
- `POST /api/loans`
- `GET /api/loans/me`
- `GET /api/loans/{id}`
- `GET /api/admin/loans`
- `POST /api/admin/loans/{id}/approve`
- `POST /api/admin/loans/{id}/reject`

### Contracts
- `GET /api/contracts/{loanId}`
- `GET /api/contracts/{loanId}/download`
- `GET /api/admin/contracts`
- `POST /api/admin/contracts/generate/{loanId}`
- `POST /api/admin/contracts/sign/{loanId}`
- `POST /api/admin/contracts/timestamp/{loanId}`

### Compliance and Audit
- `POST /api/admin/compliance/verify`
- `GET /api/admin/compliance/reports`
- `GET /api/admin/audit-logs`

## Note

Legacy marketplace/lender/pledge/repayment flows were removed as part of the hard cut to the enhanced HLD scope.
