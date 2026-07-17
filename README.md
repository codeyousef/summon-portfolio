# Yousef — Portfolio · Blog · Docs

A single Ktor + Summon stack powering three surfaces:

- Portfolio: featured work, services, contact
- Blog: long-form notes and release write-ups (EN/AR)
- Docs: multi-page product manuals with sidebar, search, and deep links

Live:

- https://www.yousef.codes — portfolio, services, blog, admin
- https://summon.yousef.codes — docs (Summon, more coming)

Tech:

- Kotlin (JVM, JS), Ktor, Summon UI, SSR + hydration
- Type-safe modifiers, portals, async validation, and responsive components

## Running Locally

```bash
# Development mode (uses local file storage)
./gradlew run

# Build and run with Docker (with persistent storage)
docker-compose up -d

# Or manual Docker with named volume for persistence
docker build -t portfolio .
docker run -d -p 8080:8080 -v portfolio-data:/app/storage portfolio
```

## Deployment Notes

**Data Persistence**: In local file-storage mode, admin credentials and content are stored in `/app/storage/` inside the container. To persist data across container restarts:

- **Docker Compose**: Uses a named volume `portfolio-data` by default
- **Docker**: Use `-v portfolio-data:/app/storage` or bind mount a host directory
- **Kubernetes**: Create a PersistentVolumeClaim mounted at `/app/storage`

Cloud Run deployments should use Firestore for content and GCS for photography uploads. The GitHub deploy workflow creates/uses `portfolio-476219-portfolio-uploads` and sets `PHOTOGRAPHY_UPLOAD_BUCKET` plus a per-service `PHOTOGRAPHY_UPLOAD_PREFIX` so uploaded media survives new revisions.

The isolated Seen registry has a public [signing operations runbook](registry-service/docs/signing-operations.md) covering offline custody, ceremonies, renewal, rotation, compromise recovery, IAM policy gates, and development drills.

Environment variables:
- `PORTFOLIO_CONTENT_PATH` - Path to content.json (default: `/app/storage/content.json`)
- `ADMIN_CREDENTIALS_PATH` - Path to admin-credentials.json (default: `/app/storage/admin-credentials.json`)
- `USE_LOCAL_STORE` - Set to `true` to use file storage instead of Firestore
- `PHOTOGRAPHY_UPLOAD_BUCKET` - Optional GCS bucket for durable photo uploads in production
- `PHOTOGRAPHY_UPLOAD_PREFIX` - GCS object prefix for photo uploads (default: `photography`)
- `PHOTOGRAPHY_UPLOAD_DIR` - Local photo upload directory when no bucket is configured
- `PHOTOGRAPHY_MAX_UPLOAD_BYTES` - Maximum single photo upload size in bytes

Contributing:

- Open issues with a clear scope; include URLs/sections and screenshots if visual
- Localization in EN/AR; match copy tone and direction

© 2025 Yousef. All rights reserved.
