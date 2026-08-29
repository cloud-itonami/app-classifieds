import { callTool, jsonRpcResponse, VERSION } from "../scripts/protocol.mjs";

const sensitiveKeys = new Set([
  "address", "postaladdress", "postal_address", "street", "streetaddress",
  "postcode", "postalcode", "zip", "zipcode", "phone", "phonenumber",
]);

function json(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers },
  });
}

function agentCard(origin) {
  return {
    name: "Cloud Itonami Classifieds Match Agent",
    description: "Consent-bound Need/Seed discovery and explainable introductions.",
    supportedInterfaces: [
      { url: `${origin}/a2a`, protocolBinding: "JSONRPC", protocolVersion: "1.0" },
      { url: origin, protocolBinding: "HTTP+JSON", protocolVersion: "1.0" },
    ],
    provider: { organization: "cloud-itonami", url: "https://github.com/cloud-itonami/app-classifieds" },
    version: VERSION,
    documentationUrl: "https://github.com/cloud-itonami/app-classifieds",
    capabilities: { streaming: false, pushNotifications: false, extendedAgentCard: false },
    defaultInputModes: ["text/plain", "application/json"],
    defaultOutputModes: ["application/json"],
    skills: [
      { id: "need-seed-intake", name: "Need/Seed intake", description: "Normalize consented, provenance-bearing demand and supply records.", tags: ["classifieds", "intake"] },
      { id: "ossekkai-match", name: "Ossekkai matching", description: "Propose explainable matches without contacting either party.", tags: ["matching", "consent"] },
      { id: "nakoudo-introduction", name: "Nakoudo introduction", description: "Describe the bilateral-consent gate for a private introduction.", tags: ["introduction", "privacy"] },
    ],
    "x-effects": { contactsParties: false, movesMoney: false, booksCarrier: false },
  };
}

function hasSensitiveAddress(value) {
  if (!value || typeof value !== "object") return false;
  if (Array.isArray(value)) return value.some(hasSensitiveAddress);
  return Object.entries(value).some(([key, child]) => sensitiveKeys.has(key.toLowerCase()) || hasSensitiveAddress(child));
}

async function parseJson(request) {
  try { return await request.json(); }
  catch { throw Object.assign(new Error("invalid-json"), { status: 400 }); }
}

async function serviceHealth(binding, service) {
  if (!binding?.fetch) return { service, connected: false, status: "binding-missing" };
  try {
    const response = await binding.fetch("https://service.internal/health");
    const body = await response.json();
    return { service, connected: response.ok, status: response.ok ? "ready" : "unready", upstreamStatus: response.status, upstream: body };
  } catch (cause) {
    return { service, connected: false, status: "unreachable", error: String(cause?.message ?? cause) };
  }
}

async function connectorStatus(env) {
  const [settlement, fulfillment] = await Promise.all([
    serviceHealth(env.SETTLEMENT, "cloud-itonami-marketplace-settlement"),
    serviceHealth(env.FULFILLMENT, "cloud-itonami-marketplace-fulfillment"),
  ]);
  return {
    settlement,
    fulfillment,
    signal: {
      protocol: "org-signal X3DH + Double Ratchet",
      browserRoundTrip: "verified",
      messengerDelivery: false,
      status: "signal-messenger-account-not-configured",
    },
    carrier: { connected: fulfillment.connected, booking: false, status: "capability-only-no-carrier-booking" },
    payment: { connected: settlement.connected, moneyMovement: false, status: "proposal-only-human-authorized-external-rail" },
  };
}

async function shipmentCapability(body) {
  if (hasSensitiveAddress(body)) {
    return json({ status: "rejected", reason: "postal-address-not-accepted", disclosure: "carrier-only" }, 422);
  }
  const required = ["transactionId", "carrierId", "recipientOpaqueId", "expiresAt"];
  if (required.some((key) => typeof body[key] !== "string" || body[key].trim() === "")) {
    return json({ status: "rejected", reason: "missing-capability-input", required }, 422);
  }
  const material = `${crypto.randomUUID()}\u0000${body.transactionId}\u0000${body.carrierId}\u0000${body.recipientOpaqueId}\u0000${body.expiresAt}`;
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(material));
  const token = btoa(String.fromCharCode(...new Uint8Array(digest))).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
  return json({
    status: "issued", capability: `shipcap.v1.${token}`, transactionId: body.transactionId,
    carrierId: body.carrierId, expiresAt: body.expiresAt, disclosure: "carrier-only",
    containsPostalAddress: false, carrierBooked: false,
  }, 201);
}

function escrowPlan(body) {
  const required = ["escrowId", "basketId", "amountMinor", "currency"];
  if (required.some((key) => body[key] === undefined || body[key] === null || body[key] === "")) {
    return json({ status: "rejected", reason: "missing-escrow-input", required }, 422);
  }
  return json({
    status: "proposed", escrowId: body.escrowId, basketId: body.basketId,
    amountMinor: body.amountMinor, currency: body.currency,
    releaseGate: ["provider-capture", "delivery-evidence", "no-open-dispute", "named-human-authorization"],
    settlementActor: "cloud-itonami-marketplace-settlement", moneyMoved: false,
  }, 202);
}

function a2aAction(message) {
  const parts = message?.parts;
  if (!Array.isArray(parts) || parts.length === 0) throw Object.assign(new Error("message.parts-required"), { code: -32602 });
  const structured = parts.find((part) => part && typeof part.data === "object")?.data;
  if (structured?.tool) return callTool(structured.tool, structured.arguments ?? {}).structuredContent;
  const text = parts.find((part) => typeof part?.text === "string")?.text;
  if (text !== undefined) return callTool("classifieds.search", { query: text }).structuredContent;
  throw Object.assign(new Error("text-or-data-part-required"), { code: -32602 });
}

function handleA2a(request) {
  try {
    if (request?.jsonrpc !== "2.0" || !["SendMessage", "message/send"].includes(request.method)) {
      throw Object.assign(new Error("Method not found"), { code: -32601 });
    }
    const result = a2aAction(request.params?.message ?? request.params?.request?.message);
    return { jsonrpc: "2.0", id: request.id ?? null, result: { message: {
      role: "ROLE_AGENT", messageId: crypto.randomUUID(), parts: [{ data: result }],
    } } };
  } catch (cause) {
    return { jsonrpc: "2.0", id: request?.id ?? null, error: { code: cause.code ?? -32603, message: cause.message } };
  }
}

async function route(request, env) {
  const url = new URL(request.url);
  const { pathname } = url;

  if (request.method === "GET" && pathname === "/health") {
    const connectors = await connectorStatus(env);
    return json({ ok: true, service: "cloud-itonami-app-classifieds", version: VERSION, connectors });
  }
  if (request.method === "GET" && pathname === "/.well-known/agent-card.json") return json(agentCard(url.origin));
  if (request.method === "GET" && pathname === "/api/connectors/status") return json(await connectorStatus(env));
  if (request.method === "POST" && pathname === "/mcp") return json(jsonRpcResponse(await parseJson(request)));
  if (request.method === "POST" && pathname === "/a2a") return json(handleA2a(await parseJson(request)));
  if (request.method === "POST" && pathname === "/message:send") {
    try {
      const result = a2aAction((await parseJson(request)).message);
      return json({ message: { role: "ROLE_AGENT", messageId: crypto.randomUUID(), parts: [{ data: result }] } }, 200, { "content-type": "application/a2a+json" });
    } catch (cause) { return json({ error: cause.message }, cause.status ?? 422, { "content-type": "application/a2a+json" }); }
  }
  if (request.method === "POST" && pathname === "/api/shipment/capability") return shipmentCapability(await parseJson(request));
  if (request.method === "POST" && pathname === "/api/escrow/plan") return escrowPlan(await parseJson(request));
  if (request.method === "POST" && ["/api/escrow/release", "/api/payment/capture", "/api/payment/transfer"].includes(pathname)) {
    return json({ status: "refused", reason: "money-movement-out-of-scope", moneyMoved: false }, 403);
  }
  if (request.method === "POST" && pathname === "/api/signal/send") {
    return json({ status: "refused", reason: "signal-messenger-account-not-configured", delivered: false }, 503);
  }
  if (pathname.startsWith("/api/") || pathname === "/mcp" || pathname === "/a2a") return json({ error: "not-found" }, 404);
  return env.ASSETS.fetch(request);
}

async function fetch(request, env) {
  try { return await route(request, env); }
  catch (cause) { return json({ error: cause.message ?? "internal-error" }, cause.status ?? 500); }
}

export default { fetch };
export { agentCard, hasSensitiveAddress, route };
