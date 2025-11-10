# Yousef Baitalmal — Portfolio · Lab Notes · Docs

> A single Ktor/Summon stack that handles the entire public presence:
> *pitch → project stories → services → long-form documentation.*
> Currently, the docs surface highlights **Summon**, with additional product handbooks slated to join the same flow.

## Why This Repo Exists

- **Flagship portfolio** – the glass-and-neon landing page showcasing active collaborations, metrics, and immediate
  CTAs.
- **Services + admin overlays** – Summon UI that walks prospects through offerings and locks in contact without exposing
  email.
- **Bilingual blog surface** – editorial highlights in EN + AR to demonstrate process depth.
- **Docs hub** – `/docs` streams Markdown directly from GitHub and renders it as full multi-page manuals (sidebar, TOC,
  prev/next). Today it serves Summon; tomorrow it will host every product playbook.

## Production Signals

| Domain                | Purpose                                     |
|-----------------------|---------------------------------------------|
| `www.yousef.codes`    | Portfolio, services, blog, and admin entry. |
| `summon.yousef.codes` | Docs host (currently Summon).               |

No setup commands are published here—this repo is purely for what ships to those domains. If something needs tweaking,
reference the section or doc slug and file an issue so it can be designed, localized, and shipped intentionally.
