# ScoreGrid — frontend

React 19 · TypeScript 6 · Vite 8 · Tailwind CSS 4 · shadcn/ui

```bash
npm install
npm run dev        # :5173, proxies nothing — talks to the gateway on :8080
npm run build      # tsc -b && vite build
npm run lint       # oxlint
```

The dev server expects the API gateway to be reachable. Start it with the rest of the system, or just the infrastructure and the services you need:

```bash
docker compose up -d          # from the repository root
```

---

## Language

**UI copy is Spanish. Everything else is English.** Component names, props, comments, commit messages and API fields are English; every string a participant reads is Spanish, in the Rioplatense register used by the interface mock. This is hard rule 10 — see [`AGENTS.md`](../AGENTS.md#4-hard-rules).

---

## Layout

```
src/
├── index.css              design tokens — change colours HERE, never in a component
├── lib/
│   ├── api.ts             the one axios client; attaches the JWT, handles 401
│   └── utils.ts           cn() — clsx + tailwind-merge
├── auth/                  AuthContext + RequireAuth route guard
├── components/
│   ├── ui/                shadcn primitives, restyled to the mock
│   ├── layout/            AppLayout, Sidebar, Topbar, usePageHeader
│   └── common/            states, FormField, StatusBadge, MetricCard, PageTitle
├── features/<area>/       screens, owned by the stream that owns the area
└── App.tsx                route map
```

`@/` resolves to `src/`. The alias is declared in `tsconfig.app.json` under `paths` and mirrored in `vite.config.ts` — both must agree or the build and the editor disagree about the same import.

---

## The design system

Owned by Bernard (Stream A). **Import it; do not edit it.** If you need a variant that does not exist, ask — it gets added once, for all three streams, instead of three times slightly differently.

The full component inventory is in [`AGENTS.md`](../AGENTS.md#the-design-system). The parts that are easy to miss:

| Thing | Note |
|-------|------|
| `usePageHeader(title, subtitle)` | Sets the topbar heading from inside a screen. Do not add a route-to-title map to `AppLayout` — that file is shared |
| `EmptyState` / `ErrorState` / `LoadingState` | Use these three. `ErrorState` takes `onRetry`; an error with no way forward is a dead end |
| `FormField` | Render-prop: it hands your control an `id`, `aria-describedby` and `aria-invalid`. Spread them |
| `TournamentStatusBadge` / `MatchStatusBadge` | Contract status → Spanish label and colour, in one place. Do not translate `FINISHED` yourself |
| `Button` | Extra variants from the mock: `success` (green, confirm) and `size="block"` (full width) |
| `TabsList` | `variant="pill"` for the mock's free-standing pill tabs |

Tailwind 4 has **no `tailwind.config.js`** — tokens live in `src/index.css` under `@theme`, and the plugin is registered in `vite.config.ts`.

Adding another shadcn component:

```bash
npx shadcn@latest add <component>
```

It generates into `src/components/ui/`. Restyle it to the mock afterwards; the generated defaults are not our design.
