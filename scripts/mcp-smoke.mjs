import { spawn } from "node:child_process";
import readline from "node:readline";

const child = spawn(process.execPath, ["scripts/mcp-server.mjs"], { stdio: ["pipe", "pipe", "inherit"] });
const lines = readline.createInterface({ input: child.stdout });
const pending = new Map();
lines.on("line", (line) => {
  const message = JSON.parse(line);
  pending.get(message.id)?.(message);
  pending.delete(message.id);
});

let id = 0;
function request(method, params) {
  const requestId = ++id;
  child.stdin.write(`${JSON.stringify({ jsonrpc: "2.0", id: requestId, method, params })}\n`);
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`timeout: ${method}`)), 5000);
    pending.set(requestId, (message) => {
      clearTimeout(timer);
      if (message.error) reject(new Error(message.error.message));
      else resolve(message.result);
    });
  });
}

try {
  const initialized = await request("initialize", {});
  const listed = await request("tools/list", {});
  const intake = await request("tools/call", {
    name: "classifieds.intake.normalize",
    arguments: { id: "need-1", kind: "need", category: "sale", region: "kanto",
      actorDid: "did:example:need-1", title: "自転車を探しています",
      source: "feed.example", observedAt: "2026-08-29T00:00:00Z" },
  });
  const match = await request("tools/call", {
    name: "classifieds.match.propose",
    arguments: {
      need: { kind: "need", actorDid: "did:example:a", category: "sale", region: "kanto", terms: ["bicycle"] },
      seed: { kind: "seed", actorDid: "did:example:b", category: "sale", region: "kanto", terms: ["bicycle"] },
    },
  });
  if (initialized.serverInfo.name !== "app-classifieds") throw new Error("server identity mismatch");
  if (listed.tools.length !== 3) throw new Error("tool count mismatch");
  if (intake.structuredContent.status !== "accepted") throw new Error("intake failed");
  if (match.structuredContent.status !== "proposed" || match.structuredContent.contacted !== false) {
    throw new Error("governed match failed");
  }
  console.log(JSON.stringify({ status: "ok", protocolVersion: initialized.protocolVersion,
    tools: listed.tools.length, intake: intake.structuredContent.status,
    match: match.structuredContent.status, contacted: match.structuredContent.contacted }));
} finally {
  child.stdin.end();
  child.kill();
}
