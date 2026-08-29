import assert from "node:assert/strict";
import test from "node:test";
import worker, { route } from "../worker/index.js";

const healthyService = (service) => ({
  fetch: async () => new Response(JSON.stringify({ ok: true, service }), {
    status: 200, headers: { "content-type": "application/json" },
  }),
});

const env = {
  SETTLEMENT: healthyService("settlement"),
  FULFILLMENT: healthyService("fulfillment"),
  ASSETS: { fetch: async () => new Response("asset", { status: 200 }) },
};

const request = (path, body, method = "POST") => new Request(`https://example.test${path}`, {
  method,
  headers: body === undefined ? undefined : { "content-type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

test("health reports service-binding readiness and honest external effects", async () => {
  const response = await route(request("/health", undefined, "GET"), env);
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.connectors.settlement.connected, true);
  assert.equal(body.connectors.fulfillment.connected, true);
  assert.equal(body.connectors.signal.messengerDelivery, false);
  assert.equal(body.connectors.payment.moneyMovement, false);
});

test("A2A Agent Card declares the live v1 JSON-RPC and HTTP interfaces", async () => {
  const response = await route(request("/.well-known/agent-card.json", undefined, "GET"), env);
  const body = await response.json();
  assert.equal(body.supportedInterfaces[0].url, "https://example.test/a2a");
  assert.equal(body.supportedInterfaces[0].protocolBinding, "JSONRPC");
  assert.equal(body.supportedInterfaces[0].protocolVersion, "1.0");
  assert.equal(body["x-effects"].movesMoney, false);
});

test("HTTP MCP exposes and calls the shared tools", async () => {
  const listed = await route(request("/mcp", { jsonrpc: "2.0", id: 1, method: "tools/list" }), env);
  assert.equal((await listed.json()).result.tools.length, 3);

  const called = await route(request("/mcp", {
    jsonrpc: "2.0", id: 2, method: "tools/call",
    params: { name: "classifieds.search", arguments: { query: "写真", region: "kanto" } },
  }), env);
  const body = await called.json();
  assert.equal(body.result.structuredContent.total, 1);
  assert.equal(body.result.structuredContent.provenancePreserved, true);
});

test("A2A JSON-RPC v1 SendMessage returns a direct agent message", async () => {
  const response = await route(request("/a2a", {
    jsonrpc: "2.0", id: "a2a-1", method: "SendMessage",
    params: { message: { role: "ROLE_USER", messageId: "msg-1", parts: [{ text: "写真" }] } },
  }), env);
  const body = await response.json();
  assert.equal(body.result.message.role, "ROLE_AGENT");
  assert.equal(body.result.message.parts[0].data.total, 1);
});

test("shipment capability never accepts or returns a postal address", async () => {
  const rejected = await route(request("/api/shipment/capability", {
    transactionId: "tx-1", carrierId: "carrier-1", recipientOpaqueId: "recipient-1",
    expiresAt: "2026-09-01T00:00:00Z", postalAddress: { street: "secret" },
  }), env);
  assert.equal(rejected.status, 422);
  assert.equal((await rejected.json()).reason, "postal-address-not-accepted");

  const issued = await route(request("/api/shipment/capability", {
    transactionId: "tx-1", carrierId: "carrier-1", recipientOpaqueId: "recipient-1",
    expiresAt: "2026-09-01T00:00:00Z",
  }), env);
  const body = await issued.json();
  assert.equal(issued.status, 201);
  assert.match(body.capability, /^shipcap\.v1\.[A-Za-z0-9_-]+$/);
  assert.equal(body.containsPostalAddress, false);
  assert.equal(JSON.stringify(body).includes("secret"), false);
});

test("escrow plan is inert and money-moving routes fail closed", async () => {
  const planned = await route(request("/api/escrow/plan", {
    escrowId: "esc-1", basketId: "basket-1", amountMinor: 18000, currency: "JPY",
  }), env);
  const plan = await planned.json();
  assert.equal(planned.status, 202);
  assert.equal(plan.moneyMoved, false);
  assert.deepEqual(plan.releaseGate, [
    "provider-capture", "delivery-evidence", "no-open-dispute", "named-human-authorization",
  ]);

  for (const path of ["/api/escrow/release", "/api/payment/capture", "/api/payment/transfer"]) {
    const refused = await route(request(path, {}), env);
    assert.equal(refused.status, 403);
    assert.equal((await refused.json()).moneyMoved, false);
  }
});

test("Signal Messenger delivery refuses until an account-backed bridge exists", async () => {
  const response = await route(request("/api/signal/send", { ciphertext: "opaque" }), env);
  const body = await response.json();
  assert.equal(response.status, 503);
  assert.equal(body.delivered, false);
  assert.equal(body.reason, "signal-messenger-account-not-configured");
});

test("non-API traffic falls through to static assets", async () => {
  const response = await route(request("/", undefined, "GET"), env);
  assert.equal(await response.text(), "asset");
});

test("invalid JSON is a bounded 400 response", async () => {
  const response = await worker.fetch(new Request("https://example.test/mcp", {
    method: "POST", headers: { "content-type": "application/json" }, body: "{",
  }), env);
  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "invalid-json");
});
