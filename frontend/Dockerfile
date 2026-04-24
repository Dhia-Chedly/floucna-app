# ─────────────────────────────────────────────
# Stage 1: Install dependencies
# ─────────────────────────────────────────────
FROM node:20-alpine AS deps

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --frozen-lockfile

# ─────────────────────────────────────────────
# Stage 2: Build Next.js standalone
# ─────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# NEXT_PUBLIC_ vars must be available at build time
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

RUN npm run build

# ─────────────────────────────────────────────
# Stage 3: Production runner (minimal image)
# ─────────────────────────────────────────────
FROM node:20-alpine AS runner

WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000

# Non-root user
RUN addgroup --system floucna && adduser --system --ingroup floucna floucna

COPY --from=builder --chown=floucna:floucna /app/.next/standalone ./
COPY --from=builder --chown=floucna:floucna /app/.next/static ./.next/static
COPY --from=builder --chown=floucna:floucna /app/public ./public

USER floucna

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:3000 || exit 1

CMD ["node", "server.js"]
