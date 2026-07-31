# Dual-agent and code-intelligence architecture

`config/r4r-agents.json` is the only versioned source of model IDs, endpoints' env
names, context/output limits, queues, ownership and MCP selection. `.env.r4r.local`
contains only machine-specific overrides.

Run `npm run agent:pc` and `npm run agent:lp` in separate terminals, or use
`npm run agent:both`. Both edit the same branch but disjoint product paths; neither
commits. Review and commit manually.

Reference repositories are declared in `knowledge/code-repositories.md`. `npm run
repos:sync` clones fixed revisions under `.r4r/`; `npm run code:index` rebuilds the
root CodeGraph (including opted-in nested repos) and refreshes the shared
Code-Graph-RAG Memgraph/Qdrant projects.
