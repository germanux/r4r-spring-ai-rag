#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { loadAgentConfig, mergedEnvironment, normalizeV1Endpoint, repositoryRoot } from "./lib/r4r-config.mjs";

const root = repositoryRoot();
let destination = "";
let printOnly = false;
for (let i = 2; i < process.argv.length; i += 1) {
  const arg = process.argv[i];
  if (arg === "--destination") destination = String(process.argv[++i] ?? "").toUpperCase();
  else if (arg === "--print") printOnly = true;
  else throw new Error(`Unknown option: ${arg}`);
}
if (!destination) destination = String(process.env.R4R_DESTINATION ?? "PC").toUpperCase();
if (!["PC", "LP"].includes(destination)) throw new Error("Use --destination PC or LP");

const canonical = loadAgentConfig(root);
const profile = canonical.agents[destination];
const env = mergedEnvironment(root);
const endpoint = normalizeV1Endpoint(env[profile.endpointEnv] ?? profile.defaultEndpoint, profile.endpointEnv);
const cgr = canonical.codeIntelligence.codeGraphRag;
const cgrEndpoint = normalizeV1Endpoint(env[cgr.endpointEnv] ?? cgr.defaultEndpoint, cgr.endpointEnv);
const cgrModel = env[cgr.modelEnv] ?? cgr.defaultModel;

const source = path.join(root, "opencode.jsonc");
const resolved = JSON.parse(fs.readFileSync(source, "utf8"));
resolved.default_agent = profile.agentId;
resolved.provider ??= {};
resolved.provider[profile.provider] ??= {
  npm: "@ai-sdk/openai-compatible",
  name: profile.provider,
  options: {},
  models: {},
};
const provider = resolved.provider[profile.provider];
provider.options ??= {};
provider.options.baseURL = endpoint;
provider.options.timeout = canonical.defaults.requestTimeoutSeconds * 1000;
provider.options.chunkTimeout = canonical.defaults.chunkTimeoutSeconds * 1000;
provider.models = {
  [profile.model]: {
    name: profile.modelLabel,
    temperature: true,
    limit: { context: profile.contextTokens, output: profile.outputTokens },
  },
};
for (const [name, item] of Object.entries(resolved.mcp ?? {})) {
  if (item && typeof item === "object") item.enabled = profile.mcp.includes(name);
}
if (resolved.mcp?.code_graph_rag) {
  resolved.mcp.code_graph_rag.environment = {
    TARGET_REPO_PATH: root,
    ORCHESTRATOR_PROVIDER: "ollama",
    ORCHESTRATOR_MODEL: cgrModel,
    ORCHESTRATOR_ENDPOINT: cgrEndpoint,
    CYPHER_PROVIDER: "ollama",
    CYPHER_MODEL: cgrModel,
    CYPHER_ENDPOINT: cgrEndpoint,
  };
}

const controlDir = path.resolve(root, profile.controlDir);
fs.mkdirSync(controlDir, { recursive: true });
const configPath = path.join(controlDir, "opencode.resolved.json");
fs.writeFileSync(configPath, JSON.stringify(resolved, null, 2) + "\n");
const metadata = {
  schemaVersion: 1,
  destination,
  agent: profile.agentId,
  role: profile.role,
  provider: profile.provider,
  model: profile.model,
  endpoint,
  worker: profile.worker,
  plan: profile.plan,
  progress: profile.progress,
  memory: profile.memory,
  controlDir: profile.controlDir,
  peerPaths: profile.peerPaths,
  allowedPaths: profile.allowedPaths,
  runtime: {
    maxAttemptsPerTask: canonical.defaults.maxAttemptsPerTask,
    maxNoProgressCycles: canonical.defaults.maxNoProgressCycles,
    maxTransientFailures: canonical.defaults.maxTransientFailures,
    autoCommit: canonical.defaults.autoCommit,
    bootstrapCommit: canonical.defaults.bootstrapCommit,
    checkpointOnGreen: canonical.defaults.checkpointOnGreen,
    maxSessionSeconds: profile.runtime?.maxSessionSeconds ?? canonical.defaults.maxSessionSeconds,
    idleSeconds: profile.runtime?.idleSeconds ?? canonical.defaults.idleSeconds,
    maxSessionSteps: profile.runtime?.maxSessionSteps ?? canonical.defaults.maxSessionSteps,
    repeatEventBudget: profile.runtime?.repeatEventBudget ?? canonical.defaults.repeatEventBudget,
  },
  opencodeConfig: path.relative(root, configPath),
};
const metadataPath = path.join(controlDir, "agent-runtime.json");
fs.writeFileSync(metadataPath, JSON.stringify(metadata, null, 2) + "\n");
if (printOnly) process.stdout.write(JSON.stringify(metadata, null, 2) + "\n");
else process.stdout.write(metadataPath + "\n");
