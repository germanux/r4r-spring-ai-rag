#!/usr/bin/env node
import path from "node:path";
import fs from "node:fs";
import { loadAgentConfig, loadRepositoryManifest, repositoryRoot, run } from "./lib/r4r-config.mjs";

const root = repositoryRoot();
const cgrOnly = process.argv.includes("--cgr-only");
const codegraphOnly = process.argv.includes("--codegraph-only");
const skipSync = process.argv.includes("--skip-sync");
if (cgrOnly && codegraphOnly) throw new Error("Choose only one indexer");
if (!skipSync) run(process.execPath, [path.join(root, "scripts", "sync-code-repositories.mjs")], { cwd: root });
const config = loadAgentConfig(root);
const { manifest, enabled } = loadRepositoryManifest(root, config);

if (!cgrOnly) {
  const db = path.join(root, ".codegraph", "codegraph.db");
  run(process.env.R4R_CODEGRAPH_BIN ?? "codegraph", [fs.existsSync(db) ? "index" : "init"], { cwd: root });
  console.log("OK CodeGraph: root project plus opted-in nested repositories indexed");
}

if (!codegraphOnly) {
  const cgr = path.join(root, "scripts", "cgr.sh");
  run("bash", [cgr, "up"], { cwd: root });
  run("bash", [cgr, "index-path", root, "r4r-application"], { cwd: root });
  for (const repo of enabled.filter((item) => item.indexers?.codeGraphRag !== false)) {
    run("bash", [cgr, "index-path", path.resolve(root, repo.localDirectory), repo.projectName], { cwd: root });
  }
  run("bash", [cgr, "workspace-create", manifest.workspace], { cwd: root, allowFailure: true });
  run("bash", [cgr, "workspace-add", manifest.workspace, root], { cwd: root, allowFailure: true });
  for (const repo of enabled.filter((item) => item.indexers?.codeGraphRag !== false)) {
    run("bash", [cgr, "workspace-add", manifest.workspace, path.resolve(root, repo.localDirectory)], { cwd: root, allowFailure: true });
  }
  console.log(`OK Code-Graph-RAG workspace: ${manifest.workspace}`);
}
