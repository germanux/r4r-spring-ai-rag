# R4R dual-agent overlay

1. Copy `.env.r4r.local.example` to `.env.r4r.local` and adjust endpoints.
2. Run `npm run config:check`.
3. Run `npm run repos:sync` and `npm run code:index` when reference graphs are needed.
4. Start `npm run agent:pc` and `npm run agent:lp` in separate terminals, or use
   `npm run agent:both`.
5. Review `src/**` and `frontend/**`, then commit manually. Agents never commit.
