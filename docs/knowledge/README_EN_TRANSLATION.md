# English translation — R4R corpus

The Spanish reference corpus has **41 original Markdown documents** in `knowledge/es/`.
The `knowledge/en/` directory includes **5 existing complete English translations** of matching numbered documents (80, 81, 101, 102, 105).

The English corpus is **not complete yet**. Translate the other 36 documents with the provided local-Ollama tool:

```bash
python3 translate_es_to_en.py --root . --ollama http://127.0.0.1:11434 --model qwen3:8b
```

This requires your locally-running Ollama with `qwen3:8b`; no additional Python packages. The script resumes by skipping completed documents and caching translated batches. It writes numbered English equivalents under `knowledge/en/` without modifying Spanish originals. To refresh translations use `--force` (existing cache still used for unchanged batches).

**Quality control required:** Machine translations of legal/technical advice, historic projects, quantities, links and Markdown tables must be reviewed before ingesting into production. Match each `R4R_<number>_...md` document in English to its Spanish source; ensure no Spanish remains in English explanations, no lost content, and confirm source/citation URLs. Do not run production ingestion before checking translations.

Existing files other than the five equivalent English documents may have had English-only translations in an earlier draft. They were excluded here because no Spanish counterpart exists and the user requested a one-to-one translated corpus.
