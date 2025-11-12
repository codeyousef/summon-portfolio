# Remaining Work

## High Priority
1. **Docs search shell:** Replace the not-found search markup/script in `DocsRouter` with Summon components and state so there is no embedded `<div>`/`<script>` block. Make sure filtering happens in Kotlin (or via Summon effect hooks) and styling uses modifiers.
2. **Admin dashboard:** Eliminate `RawHtml` in `AdminDashboardPage` (forms, modals, rendered fragments). Swap checkbox/list markup for Summon inputs, and move the renderFragment helper logic into reusable components so no manual HTML is emitted.
3. **Summon landing + marketing sections:** Finish migrating any remaining `RawHtml` snippets (e.g., testimonials/projects that still inject HTML elsewhere) to `RichText`/text components, and update the CTA/link modifiers to use typed enums instead of strings.
4. **Docs prose typography:** Apply heading/link/table styles via modifiers or scoped components (e.g., wrap `RichText` output in Summon nodes that enforce spacing) so we can delete the old global prose styles entirely.

## Follow-Up
- Sweep the entire repo for `.style(...)`, string-based modifier parameters (e.g., `"none"` for `textDecoration`), and `RawHtml`. Replace them with typed modifiers or dedicated components everywhere except the aurora background script.
- Add light-touch tests or snapshots around the new docs components once their behavior is fully declarative.

---

**Reminder to future me:** Avoid using `RawHtml`, inline `<style>`/`<script>`, `.style(...)`, and string literal modifier parameters. Always reach for Summon components (`AnchorLink`, `Button`, `Image`, `RichText`, etc.) and typed modifier APIs so the UI stays declarative. The only sanctioned raw script remains the aurora background loader.*** End Patch
