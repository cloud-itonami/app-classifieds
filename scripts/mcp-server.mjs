#!/usr/bin/env node
import readline from "node:readline";
import { jsonRpcResponse } from "./protocol.mjs";

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on("line", (line) => {
  let request;
  try {
    request = JSON.parse(line);
    const response = jsonRpcResponse(request);
    if (request.id !== undefined && response !== undefined) {
      process.stdout.write(`${JSON.stringify(response)}\n`);
    }
  } catch (cause) {
    const payload = { jsonrpc: "2.0", id: request?.id ?? null, error: { code: -32700, message: cause.message } };
    process.stdout.write(`${JSON.stringify(payload)}\n`);
  }
});
