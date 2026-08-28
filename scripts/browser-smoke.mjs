const port = Number(process.env.CLASSIFIEDS_CDP_PORT || 9229);
const pages = await fetch(`http://127.0.0.1:${port}/json`).then((r) => r.json());
const page = pages.find((p) => p.type === "page");
if (!page) throw new Error("No Chrome page target");

const socket = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((resolve, reject) => {
  socket.addEventListener("open", resolve, { once: true });
  socket.addEventListener("error", reject, { once: true });
});

let nextId = 0;
const pending = new Map();
socket.addEventListener("message", ({ data }) => {
  const message = JSON.parse(data);
  if (message.id && pending.has(message.id)) {
    const { resolve, reject } = pending.get(message.id);
    pending.delete(message.id);
    if (message.error) reject(new Error(message.error.message));
    else resolve(message.result);
  }
});

function call(method, params = {}) {
  const id = ++nextId;
  socket.send(JSON.stringify({ id, method, params }));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}

async function evaluate(expression) {
  const result = await call("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text);
  return result.result.value;
}

const ready = await evaluate("document.readyState");
if (ready === "loading") await new Promise((resolve) => setTimeout(resolve, 500));

const observed = await evaluate(`(() => {
  localStorage.removeItem("cloud-itonami.app-classifieds.local-listings.v1");
  document.querySelectorAll('[data-listing-id^="local-"]').forEach((el) => el.remove());
  const count = () => Number(document.getElementById("result-count").textContent);
  const change = (id, value, eventName) => {
    const el = document.getElementById(id);
    el.value = value;
    el.dispatchEvent(new Event(eventName, { bubbles: true }));
  };
  change("region-filter", "all", "change");
  change("query-filter", "", "input");
  document.querySelector('[data-category-filter="all"]').click();
  const result = { initial: count() };
  change("region-filter", "kanto", "change");
  result.kanto = count();
  document.querySelector('[data-category-filter="services"]').click();
  result.kantoServices = count();
  change("query-filter", "写真", "input");
  result.photo = count();
  document.getElementById("open-post-dialog").click();
  result.dialogOpen = document.getElementById("post-dialog").open;
  const form = document.getElementById("post-form");
  form.querySelector('[name="title"]').value = "ベビーチェア";
  form.querySelector('[name="category"]').value = "sale";
  form.querySelector('[name="region"]').value = "kanto";
  form.querySelector('[name="city"]').value = "川崎市";
  form.querySelector('[name="price"]').value = "2,000円";
  form.querySelector('[name="description"]').value = "受け渡し日時は相談できます。";
  form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
  result.afterLocalPost = count();
  result.localStored = JSON.parse(localStorage.getItem("cloud-itonami.app-classifieds.local-listings.v1")).length;
  return result;
})()`);

const expected = { initial: 8, kanto: 2, kantoServices: 1, photo: 1,
                   dialogOpen: true, afterLocalPost: 9, localStored: 1 };
if (JSON.stringify(observed) !== JSON.stringify(expected)) {
  throw new Error(`Browser smoke mismatch: ${JSON.stringify({ expected, observed })}`);
}

console.log(JSON.stringify({ status: "ok", ...observed }));
socket.close();
