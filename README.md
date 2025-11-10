# Yousef Baitalmal — Portfolio & Summon Docs

This repository contains the production source for my personal site: a Kotlin Ktor server that renders the portfolio,
services catalog, blog highlights, and the Summon framework documentation via Summon SSR. Everything in here is
dedicated to presenting client work, capturing new leads, and publishing the multi-page docs experience for Summon.

## What’s Inside

- **Portfolio homepage** – hero, selected projects, services overview, testimonials, and a lead-focused contact form.
- **Blog surface** – curated posts pulled from the content store and rendered in both English and Arabic layouts.
- **Services & admin overlay** – Summon-powered modal that highlights featured services and ties directly into the
  contact funnel.
- **Summon documentation host** – `/docs` resolves Markdown from `codeyousef/summon` and ships a multi-page experience
  with sidebar navigation, TOC, and SEO metadata.

## Structure Highlights

- `src/main/kotlin/code/yousef/portfolio/ui` – Summon composables for hero/projects/services/blog/contact sections and
  shared chrome like the header/footer.
- `src/main/kotlin/code/yousef/portfolio/docs` – Docs service, caching, link rewriting, SEO helpers, and catalog logic
  for remote GitHub content.
- `docs/private` – Planning artifacts (prompt, sprint notes) that map backlog items to implementation.

## Live Surfaces

- `www.yousef.codes` – portfolio, blog, services, admin entry.
- `summon.yousef.codes` – Summon documentation served from GitHub Markdown with SSR.

No install instructions are published here; this repo is solely for the site that visitors experience on the domains
above. To request changes or report issues, open a ticket with the specific section or doc slug that needs attention.
