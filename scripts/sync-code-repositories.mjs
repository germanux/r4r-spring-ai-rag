#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { loadAgentConfig, loadRepositoryManifest, repositoryRoot, run } from "./lib/r4r-config.mjs";

const root = repositoryRoot();
const listOnly = process.argv.includes("--list");
const offline = process.argv.includes("--offline");
const config = loadAgentConfig(root);
const { file, manifest, enabled } = loadRepositoryManifest(root, config);
if (listOnly) {
  console.log(`Manifest: ${path.relative(root, file)}`);
  for (const repo of manifest.repositories) {
    console.log(`${repo.enabled ? "ENABLED " : "disabled"} ${repo.id} ${repo.ref} -> ${repo.localDirectory}`);
  }
  process.exit(0);
}

const resolved = [];
for (const repo of enabled) {
  const target = path.resolve(root, repo.localDirectory);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  if (!fs.existsSync(path.join(target, ".git"))) {
    if (offline) throw new Error(`Missing offline repository: ${repo.localDirectory}`);
    run("git", ["clone", "--filter=blob:none", "--no-checkout", repo.url, target], { cwd: root });
  }
  run("git", ["remote", "set-url", "origin", repo.url], { cwd: target });
  if (!offline) run("git", ["fetch", "--force", "--depth", "1", "origin", repo.ref], { cwd: target });
  const revision = offline ? repo.ref : "FETCH_HEAD";
  run("git", ["checkout", "--detach", "--force", revision], { cwd: target });
  run("git", ["reset", "--hard", revision], { cwd: target });
  run("git", ["clean", "-ffd"], { cwd: target });
  const head = run("git", ["rev-parse", "HEAD"], { cwd: target, capture: true }).stdout.trim();
  fs.writeFileSync(path.join(target, ".r4r-read-only-reference"), `${repo.id}\n${head}\n`);
  resolved.push({ ...repo, absolutePath: target, head });
  console.log(`OK ${repo.id}: ${head.slice(0, 12)} ${repo.localDirectory}`);
}
const outputDir = path.join(root, "runtime", "control");
fs.mkdirSync(outputDir, { recursive: true });
fs.writeFileSync(path.join(outputDir, "repositories.resolved.json"), JSON.stringify({
  schemaVersion: 1,
  workspace: manifest.workspace,
  repositories: resolved,
}, null, 2) + "\n");
