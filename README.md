# Floucna Mina Fina 🔐
> Secure P2P Micro-Lending Platform — IT360 Cybersecurity Project

A peer-to-peer lending portal with biometric identity verification (KYC), dynamic trust scoring (Floucna Score), crowdfunded loan marketplace, PAdES digital contracts, and an admin compliance dashboard.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Next.js 18 (App Router) · TypeScript · Vanilla CSS |
| Backend | Java 17 · Javalin 6 · SQLite (WAL mode) |
| Cryptography | Europa SD-DSS · BouncyCastle · Apache PDFBox |
| Auth | JWT (JJWT) · BCrypt |
| Containerisation | Docker · Docker Compose |

---

## Quickstart — Docker (Recommended)

**Prerequisites:** Docker Desktop installed and running.

```bash
# 1. Clone the repo
git clone <repo-url>
cd "Security Project"

# 2. Build and start everything
docker compose up --build

# 3. Open the app
#    Frontend  → http://localhost:3000
#    Backend   → http://localhost:8080
#    Health    → http://localhost:8080/api/health
```

To stop:
```bash
docker compose down          # stop containers (data preserved)
docker compose down -v       # stop + wipe all data volumes
```

---

## Quickstart — Local Development

### Backend (Java)

**Prerequisites:** Java 17+, Maven 3.9+

```bash
cd backend
mvn package -DskipTests
java -jar target/backend-1.0-SNAPSHOT.jar
# → Running on http://localhost:8080
```

### Frontend (Next.js)

**Prerequisites:** Node.js 20+

```bash
cd frontend

# First time: copy env template
cp .env.example .env.local

npm install
npm run dev
# → Running on http://localhost:3000
```

---

## Project Structure

```
Security Project/
├── docker-compose.yml          # Orchestrates both services
├── .gitignore                  # Root ignore rules
├── HLD.md                      # High Level Design document
├── implementation_plan.md      # Project roadmap
│
├── backend/                    # Java / Javalin API server
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/floucna/
│       ├── Main.java
│       ├── api/                # HTTP controllers
│       │   ├── AuthController.java
│       │   ├── KycController.java
│       │   ├── LoanController.java
│       │   ├── PledgeController.java
│       │   ├── ContractController.java
│       │   ├── ComplianceController.java
│       │   └── AdminController.java
│       ├── service/            # Business logic
│       │   ├── AuthService.java
│       │   ├── KycService.java
│       │   ├── LoanService.java
│       │   ├── PledgeEngine.java
│       │   ├── ScoringEngine.java
│       │   ├── ContractService.java
│       │   └── ComplianceService.java
│       ├── db/
│       │   └── Database.java   # SQLite schema + connection
│       └── util/
│           ├── JwtUtil.java
│           └── AuditLogger.java
│
└── frontend/                   # Next.js App Router
    ├── Dockerfile
    ├── .env.example            # ← copy to .env.local
    └── src/
        ├── app/
        │   ├── page.tsx            # Landing
        │   ├── login/page.tsx
        │   ├── register/page.tsx
        │   ├── dashboard/page.tsx
        │   ├── kyc/page.tsx
        │   ├── marketplace/page.tsx
        │   ├── loans/request/page.tsx
        │   └── admin/
        │       ├── page.tsx         # Admin hub + KYC queue
        │       ├── compliance/page.tsx
        │       ├── audit/page.tsx
        │       └── users/page.tsx
        ├── components/
        │   └── Navbar.tsx
        └── lib/
            ├── api.ts              # Typed API client
            └── auth-context.tsx    # Global auth state
```

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/health` | None | Docker health check |
| POST | `/api/auth/register` | None | Register user |
| POST | `/api/auth/login` | None | Login → JWT |
| GET | `/api/auth/me` | JWT | Get own profile |
| POST | `/api/kyc/upload` | JWT | Submit KYC documents |
| GET | `/api/kyc/status` | JWT | Own KYC status |
| POST | `/api/kyc/{id}/approve?approve=true` | ADMIN | Approve/reject KYC |
| GET | `/api/loans` | JWT | Marketplace listing |
| POST | `/api/loans` | BORROWER | Create loan request |
| POST | `/api/loans/{id}/pledge` | LENDER | Fund a loan |
| GET | `/api/contracts/{loanId}` | JWT | Contract metadata |
| GET | `/api/contracts/{loanId}/download` | JWT | Download signed PDF |
| POST | `/api/contracts/{loanId}/verify` | ADMIN | Verify signature |
| GET | `/api/admin/audit-logs` | ADMIN | Audit trail |
| GET | `/api/admin/stats` | ADMIN | Platform statistics |

---

## User Roles

| Role | Capabilities |
|------|-------------|
| `BORROWER` | Register → KYC → Request loans → Repay |
| `LENDER` | Register → KYC → Browse marketplace → Pledge |
| `ADMIN` | All of the above + approve KYC + verify contracts + audit logs |

> **Create an ADMIN account:** Register normally then manually update `role = 'ADMIN'` in the SQLite DB using DBeaver or the sqlite3 CLI.

---

## Security Configuration

Set these backend environment variables in production:

| Variable | Description |
|------|-------------|
| `FLOUCNA_JWT_SECRET` | JWT signing secret (minimum 32 bytes) |
| `FLOUCNA_JWT_EXPIRY_SECONDS` | Access token lifetime in seconds (default `86400`) |
| `FLOUCNA_JWT_ISSUER` | Expected issuer claim (default `floucna-backend`) |
| `FLOUCNA_JWT_AUDIENCE` | Expected audience claim (default `floucna-frontend`) |
| `FLOUCNA_ALLOWED_ORIGINS` | Comma-separated frontend origins allowed by CORS |
| `FLOUCNA_ENABLE_DEMO_SEED` | Enables/disables demo accounts seeding (`true`/`false`) |

Notes:
- If `FLOUCNA_JWT_SECRET` is missing, the backend generates an ephemeral key at startup and tokens become invalid after restart.
- Keep demo seed disabled in production.

---

## Development Phases

- [x] **Phase 1** — Foundation: Javalin server, SQLite schema, JWT auth, base UI
- [x] **Phase 2** — KYC: File upload, webcam selfie, mock verification, score init
- [x] **Phase 3** — Marketplace: Loan creation, crowdfunding, pledge engine, scoring
- [ ] **Phase 4** — PAdES: Full SD-DSS integration, trusted TSA timestamping
- [ ] **Phase 5** — Compliance: DSS signature validator, admin verification dashboard
- [ ] **Phase 6** — Polish: Animations, E2E tests, tamper detection demo

---

## Contributing

1. Create a feature branch: `git checkout -b feature/your-feature`
2. Copy env template: `cp frontend/.env.example frontend/.env.local`
3. Start with Docker: `docker compose up --build`
4. Make your changes, test locally
5. Open a pull request — describe your changes clearly

---

## Team

IT360 Cybersecurity Project — 2026
