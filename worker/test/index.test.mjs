import assert from "node:assert/strict";
import test from "node:test";
import { handleRequest } from "../src/index.js";

const env = {
  TWELVE_DATA_API_KEY: "test-key",
  UPSTREAM_BASE_URL: "https://upstream.test",
  CACHE_TTL_SECONDS: "300",
};

test("maps a quote without exposing the upstream API key", async () => {
  const requestedUrls = [];
  const response = await handleRequest(
    new Request("https://api.example/v1/quotes/7203"),
    env,
    memoryCache(),
    async (url) => {
      requestedUrls.push(url);
      return upstream({
        symbol: "7203",
        name: "Toyota Motor",
        exchange: "JPX",
        currency: "JPY",
        close: "3245.0",
        change: "42.0",
        percent_change: "1.31",
        timestamp: 1_700_000_000,
      });
    },
  );

  assert.equal(response.status, 200);
  assert.equal(response.headers.get("x-marketpanel-cache"), "MISS");
  const body = await response.json();
  assert.deepEqual(body, {
    symbol: "7203",
    name: "Toyota Motor",
    exchange: "JPX",
    currency: "JPY",
    price: 3245,
    change: 42,
    changePercent: 1.31,
    updatedAt: 1_700_000_000,
  });
  assert.match(requestedUrls[0], /symbol=7203%3AJPX/);
  assert.doesNotMatch(JSON.stringify(body), /test-key/);
});

test("maps an ascending chart response", async () => {
  const response = await handleRequest(
    new Request("https://api.example/v1/charts/NVDA?range=1y&interval=1wk"),
    env,
    memoryCache(),
    async () => upstream({
      status: "ok",
      values: [
        { datetime: "2026-08-15 00:00:00", close: "182.48" },
        { datetime: "2026-08-08 00:00:00", close: "178.10" },
      ],
    }),
  );
  const body = await response.json();

  assert.equal(body.interval, "1week");
  assert.deepEqual(body.points.map((point) => point.value), [178.1, 182.48]);
});

test("returns a normalized error when upstream rejects a request", async () => {
  const response = await handleRequest(
    new Request("https://api.example/v1/quotes/UNKNOWN"),
    env,
    memoryCache(),
    async () => upstream({ status: "error", code: 400, message: "Unknown symbol" }),
  );

  assert.equal(response.status, 400);
  assert.equal((await response.json()).error, "upstream_error");
});

test("preserves an upstream rate-limit status", async () => {
  const response = await handleRequest(
    new Request("https://api.example/v1/quotes/ACWI"),
    env,
    memoryCache(),
    async () => upstream({ status: "error", code: 429, message: "Too many requests" }),
  );

  assert.equal(response.status, 429);
  assert.equal((await response.json()).error, "upstream_error");
});

test("serves subsequent requests from cache", async () => {
  const cache = memoryCache();
  let calls = 0;
  const request = new Request("https://api.example/v1/quotes/NVDA");
  const fetchImpl = async () => {
    calls++;
    return upstream({ symbol: "NVDA", close: "182", change: "1", percent_change: "0.5" });
  };

  await handleRequest(request, env, cache, fetchImpl);
  const response = await handleRequest(request, env, cache, fetchImpl);

  assert.equal(calls, 1);
  assert.equal(response.headers.get("x-marketpanel-cache"), "HIT");
});

test("normalizes API credit usage", async () => {
  const response = await handleRequest(
    new Request("https://api.example/v1/usage"),
    env,
    memoryCache(),
    async () => upstream({ current_usage: 3, plan_limit: 8, daily_usage: 126 }),
  );

  assert.deepEqual(await response.json(), {
    dailyUsage: 126,
    dailyLimit: 800,
    currentUsage: 3,
    minuteLimit: 8,
  });
});

test("serves a quote from persistent KV across empty edge caches", async () => {
  const kv = memoryKv();
  const kvEnv = { ...env, MARKET_CACHE: kv };
  const request = new Request("https://api.example/v1/quotes/IBM");
  let calls = 0;
  const fetchImpl = async () => {
    calls++;
    return upstream({ symbol: "IBM", close: "234", change: "1", percent_change: "0.4" });
  };

  await handleRequest(request, kvEnv, memoryCache(), fetchImpl);
  const response = await handleRequest(request, kvEnv, memoryCache(), fetchImpl);

  assert.equal(calls, 1);
  assert.equal(response.headers.get("x-marketpanel-cache"), "KV");
  assert.equal(kv.lastExpirationTtl, 7200);
});

test("stores weekly charts in KV for 24 hours", async () => {
  const kv = memoryKv();
  await handleRequest(
    new Request("https://api.example/v1/charts/IBM?range=1y&interval=1wk"),
    { ...env, MARKET_CACHE: kv },
    memoryCache(),
    async () => upstream({ values: [{ datetime: "2026-08-15 00:00:00", close: "234" }] }),
  );

  assert.equal(kv.lastExpirationTtl, 86400);
});

function upstream(body) {
  return new Response(JSON.stringify(body), { status: 200, headers: { "content-type": "application/json" } });
}

function memoryCache() {
  const values = new Map();
  return {
    async match(request) {
      const response = values.get(request.url);
      return response?.clone();
    },
    async put(request, response) {
      values.set(request.url, response.clone());
    },
  };
}

function memoryKv() {
  const values = new Map();
  return {
    lastExpirationTtl: null,
    async get(key, type) {
      const value = values.get(key);
      return type === "json" && value ? JSON.parse(value) : value ?? null;
    },
    async put(key, value, options) {
      values.set(key, value);
      this.lastExpirationTtl = options.expirationTtl;
    },
  };
}
