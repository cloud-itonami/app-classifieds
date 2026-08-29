#!/usr/bin/env node
import readline from "node:readline";

const tools = [
  {
    name: "classifieds.search",
    description: "Search public classifieds and Need/Seed intents.",
    inputSchema: { type: "object", properties: { query: { type: "string" }, region: { type: "string" } } },
  },
  {
    name: "classifieds.intake.normalize",
    description: "Normalize one consented, provenance-bearing Need or Seed record.",
    inputSchema: {
      type: "object",
      required: ["id", "kind", "category", "region", "actorDid", "title", "source"],
      properties: {
        id: { type: "string" }, kind: { type: "string", enum: ["need", "seed"] },
        category: { type: "string" }, region: { type: "string" }, actorDid: { type: "string" },
        title: { type: "string" }, source: { type: "string" }, observedAt: { type: "string" },
      },
    },
  },
  {
    name: "classifieds.match.propose",
    description: "Create an explainable ossekkai proposal without contacting either party.",
    inputSchema: {
      type: "object", required: ["need", "seed"],
      properties: { need: { type: "object" }, seed: { type: "object" } },
    },
  },
];

const listings = [
  { id: "sale-tokyo-bike", category: "sale", region: "kanto", title: "通勤用クロスバイク", source: "cloud-itonami/fleamarket" },
  { id: "service-yokohama-photo", category: "services", region: "kanto", title: "初心者向け写真レッスン", source: "cloud-itonami/hc" },
];

const content = (value) => ({ content: [{ type: "text", text: JSON.stringify(value) }], structuredContent: value });
const error = (code, message) => ({ code, message });

function normalize(input) {
  const required = ["id", "kind", "category", "region", "actorDid", "title", "source"];
  if (required.some((key) => typeof input[key] !== "string" || input[key].trim() === "")) {
    return { status: "rejected", reason: "invalid-or-unattributed-intent" };
  }
  if (!input.actorDid.startsWith("did:") || !["need", "seed"].includes(input.kind)) {
    return { status: "rejected", reason: "invalid-or-unattributed-intent" };
  }
  return {
    status: "accepted",
    intent: {
      id: input.id, kind: input.kind, category: input.category, region: input.region,
      actorDid: input.actorDid, title: input.title.trim(), source: input.source,
      observedAt: input.observedAt ?? null,
    },
    effects: [],
  };
}

function proposeMatch({ need, seed }) {
  if (need?.kind !== "need" || seed?.kind !== "seed") return { status: "rejected", reason: "need-seed-required" };
  if (need.actorDid === seed.actorDid) return { status: "rejected", reason: "self-match" };
  const facts = [];
  if (need.category === seed.category) facts.push({ reason: "same-category", points: 40 });
  if (need.region === seed.region) facts.push({ reason: "same-region", points: 25 });
  const shared = (need.terms ?? []).filter((term) => (seed.terms ?? []).includes(term));
  if (shared.length) facts.push({ reason: "shared-terms", points: Math.min(25, shared.length * 10), terms: shared });
  const score = facts.reduce((sum, fact) => sum + fact.points, 0);
  return score >= 50
    ? { status: "proposed", role: "ossekkai", score, facts, contacted: false, consents: [] }
    : { status: "rejected", reason: "insufficient-fit", score, facts };
}

function callTool(name, args = {}) {
  if (name === "classifieds.search") {
    const query = String(args.query ?? "").toLowerCase();
    const items = listings.filter((item) => (!args.region || item.region === args.region)
      && (!query || item.title.toLowerCase().includes(query)));
    return content({ items, total: items.length, provenancePreserved: true });
  }
  if (name === "classifieds.intake.normalize") return content(normalize(args));
  if (name === "classifieds.match.propose") return content(proposeMatch(args));
  throw new Error(`Unknown tool: ${name}`);
}

function handle(request) {
  if (request.method === "initialize") {
    return { protocolVersion: "2025-03-26", capabilities: { tools: {} }, serverInfo: { name: "app-classifieds", version: "0.2.0" } };
  }
  if (request.method === "tools/list") return { tools };
  if (request.method === "tools/call") return callTool(request.params?.name, request.params?.arguments);
  if (request.method?.startsWith("notifications/")) return undefined;
  throw Object.assign(new Error(`Method not found: ${request.method}`), { code: -32601 });
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on("line", (line) => {
  let request;
  try {
    request = JSON.parse(line);
    const result = handle(request);
    if (request.id !== undefined && result !== undefined) {
      process.stdout.write(`${JSON.stringify({ jsonrpc: "2.0", id: request.id, result })}\n`);
    }
  } catch (cause) {
    const payload = { jsonrpc: "2.0", id: request?.id ?? null, error: error(cause.code ?? -32603, cause.message) };
    process.stdout.write(`${JSON.stringify(payload)}\n`);
  }
});
