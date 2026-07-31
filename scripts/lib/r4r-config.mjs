import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

export function repositoryRoot(importMetaUrl = import.meta.url) {
  const here = path.dirname(fileURLToPath(importMetaUrl));
  return path.resolve(here, "../..");
}

export function parseEnvFile(file) {
  if (!fs.existsSync(file)) return {};
  const result = {};
  for (const raw of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const match = line.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (!match) throw new Error(`Invalid environment line in ${file}: ${raw}`);
    let value = match[2].trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    result[match[1]] = value;
  }
  return result;
}

export function mergedEnvironment(root) {
  return {
    ...parseEnvFile(path.join(root, ".env.r4r.local")),
    ...process.env,
  };
}

export function loadAgentConfig(root) {
  const file = path.join(root, "config", "r4r-agents.json");
  const value = JSON.parse(fs.readFileSync(file, "utf8"));
  if (value.schemaVersion !== 2 || !value.agents?.PC || !value.agents?.LP) {
    throw new Error(`Invalid canonical agent configuration: ${file}`);
  }
  return value;
}

export function loadRepositoryManifest(root, config = loadAgentConfig(root)) {
  const file = path.join(root, config.codeIntelligence.referenceManifest);
  const markdown = fs.readFileSync(file, "utf8");
  const match = markdown.match(/```json\s+r4r-code-repositories\s*\n([\s\S]*?)\n```/);
  if (!match) throw new Error(`Missing r4r-code-repositories JSON block in ${file}`);
  const manifest = JSON.parse(match[1]);
  if (manifest.schemaVersion !== 1 || !Array.isArray(manifest.repositories)) {
    throw new Error(`Invalid repository manifest: ${file}`);
  }
  const enabled = manifest.repositories.filter((item) => item.enabled);
  const maximum = Number(manifest.maxEnabledRepositories ?? config.codeIntelligence.maxEnabledRepositories);
  if (enabled.length > maximum) {
    throw new Error(`Enabled reference repositories (${enabled.length}) exceed maximum (${maximum})`);
  }
  const seen = new Set();
  for (const item of enabled) {
    for (const key of ["id", "url", "ref", "localDirectory", "projectName"]) {
      if (typeof item[key] !== "string" || !item[key].trim()) {
        throw new Error(`Repository ${item.id ?? "<unknown>"} requires ${key}`);
      }
    }
    if (seen.has(item.id)) throw new Error(`Duplicate repository id: ${item.id}`);
    seen.add(item.id);
    const target = path.resolve(root, item.localDirectory);
    const referenceRoot = path.resolve(root, config.codeIntelligence.referenceRoot);
    if (target !== referenceRoot && !target.startsWith(referenceRoot + path.sep)) {
      throw new Error(`Repository path escapes reference root: ${item.localDirectory}`);
    }
  }
  return { file, manifest, enabled };
}

export function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd,
    env: options.env ?? process.env,
    encoding: "utf8",
    stdio: options.capture ? "pipe" : "inherit",
  });
  if (result.error) throw result.error;
  if (result.status !== 0 && !options.allowFailure) {
    const detail = options.capture ? `\n${result.stdout ?? ""}\n${result.stderr ?? ""}` : "";
    throw new Error(`${command} ${args.join(" ")} failed with exit ${result.status}${detail}`);
  }
  return result;
}

export function normalizeV1Endpoint(raw, name) {
  const value = String(raw ?? "").replace(/\/+$/, "");
  const parsed = new URL(value);
  if (!["http:", "https:"].includes(parsed.protocol) || parsed.pathname.replace(/\/+$/, "") !== "/v1") {
    throw new Error(`${name} must be an HTTP(S) URL ending in /v1: ${value}`);
  }
  return value;
}
