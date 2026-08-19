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

test("uses free-plan ETF proxies for market indices", async () => {
  const requestedUrls = [];
  const responses = [
    { symbol: "VOO", name: "Vanguard S&P 500 ETF", close: "600", change: "2", percent_change: "0.3" },
    { values: [{ datetime: "2026-08-15 00:00:00", close: "600" }] },
  ];
  const response = await handleRequest(
    new Request("https://api.example/v1/markets/SP500"),
    env,
    memoryCache(),
    async (url) => {
      requestedUrls.push(url);
      return upstream(responses.shift());
    },
  );

  assert.equal(response.status, 200);
  assert.equal(requestedUrls.length, 2);
  assert.ok(requestedUrls.every((url) => url.includes("symbol=VOO")));
  assert.match(requestedUrls[1], /interval=1week/);
  assert.match(requestedUrls[1], /outputsize=52/);
  assert.equal((await response.json()).id, "SP500");
});

test("maps the actual Nikkei 225 in yen from Yahoo", async () => {
  const requestedUrls = [];
  const response = await handleRequest(
    new Request("https://api.example/v1/markets/NIKKEI225"),
    env,
    memoryCache(),
    async (url) => {
      requestedUrls.push(url);
      return upstream({ chart: { result: [{
        meta: {
          regularMarketPrice: 48_000,
          chartPreviousClose: 47_500,
          regularMarketTime: 1_700_000_000,
          currency: "JPY",
          fullExchangeName: "Osaka",
        },
        timestamp: [1_699_395_200, 1_700_000_000],
        indicators: { quote: [{ close: [47_500, 48_000] }] },
      }], error: null } });
    },
  );

  const body = await response.json();
  assert.equal(requestedUrls.length, 1);
  assert.match(requestedUrls[0], /%5EN225|%255EN225/);
  assert.match(requestedUrls[0], /interval=1d/);
  assert.equal(body.quote.currency, "JPY");
  assert.equal(body.quote.price, 48_000);
  assert.equal(body.points.length, 2);
});

test("maps added market indicators to free-plan proxies", async () => {
  const cases = [
    ["DOW30", "DIA"],
    ["NASDAQ100", "QQQ"],
  ];
  for (const [id, proxy] of cases) {
    const requestedUrls = [];
    const responses = [
      { symbol: proxy, name: proxy, close: "100", change: "1", percent_change: "1" },
      { values: [{ datetime: "2026-08-15 00:00:00", close: "100" }] },
    ];
    const response = await handleRequest(
      new Request(`https://api.example/v1/markets/${id}`),
      env,
      memoryCache(),
      async (url) => {
        requestedUrls.push(url);
        return upstream(responses.shift());
      },
    );

    assert.equal(response.status, 200);
    assert.ok(requestedUrls.every((url) => url.includes(`symbol=${proxy}`)));
    assert.equal((await response.json()).id, id);
  }
});

test("maps the actual VIX index from Yahoo without using Twelve Data", async () => {
  const requestedUrls = [];
  const response = await handleRequest(
    new Request("https://api.example/v1/markets/VIX"),
    env,
    memoryCache(),
    async (url) => {
      requestedUrls.push(url);
      return upstream({
        chart: {
          result: [{
            meta: {
              regularMarketPrice: 17.5,
              chartPreviousClose: 16,
              regularMarketTime: 1_700_086_400,
              currency: "USD",
              fullExchangeName: "Cboe Indices",
            },
            timestamp: [1_700_000_000, 1_700_043_200, 1_700_086_400],
            indicators: { quote: [{ close: [16, 0, 17.5] }] },
          }],
          error: null,
        },
      });
    },
  );

  const body = await response.json();
  assert.equal(requestedUrls.length, 1);
  assert.match(requestedUrls[0], /query1\.finance\.yahoo\.com/);
  assert.equal(body.quote.name, "VIX指数 • 遅延値");
  assert.equal(body.quote.currency, "PCT");
  assert.equal(body.quote.price, 17.5);
  assert.equal(body.quote.change, 1.5);
  assert.deepEqual(body.points.map((point) => point.value), [17.5]);
});

test("falls back to Cboe daily VIX history when Yahoo fails", async () => {
  const requestedUrls = [];
  const response = await handleRequest(
    new Request("https://api.example/v1/markets/VIX"),
    env,
    memoryCache(),
    async (url) => {
      requestedUrls.push(url);
      if (url.includes("yahoo")) return new Response("{}", { status: 503, headers: { "content-type": "application/json" } });
      return new Response("DATE,OPEN,HIGH,LOW,CLOSE\n08/17/2026,16,17,15,16.5\n08/18/2026,17,18,16,17.2", { status: 200 });
    },
  );

  const body = await response.json();
  assert.equal(requestedUrls.length, 2);
  assert.match(requestedUrls[1], /cdn\.cboe\.com/);
  assert.equal(body.quote.name, "VIX指数 • 前営業日終値");
  assert.equal(body.quote.currency, "PCT");
  assert.equal(body.quote.price, 17.2);
  assert.ok(Math.abs(body.quote.change - 0.7) < 0.000001);
});

test("maps a domestic fund NAV and one-year weekly history without Twelve Data credits", async () => {
  const requestedUrls = [];
  const rows = Array.from({ length: 52 }, (_, index) => ({
    date: `2026/${String(Math.floor(index / 4) + 1)}/${String(index % 4 + 1)}`,
    price: String(38_000 + index),
    priceChange: index === 0 ? "-59" : "+1",
    netAssetsBalance: "1",
  }));
  const escaped = JSON.stringify(rows).replaceAll('"', '\\"');
  const html = `<script>historyTable\\":{\\"items\\":${escaped},\\"hasNext\\":true}</script>`;
  const response = await handleRequest(
    new Request("https://api.example/v1/funds/EMAXIS_ALL_COUNTRY"),
    env,
    memoryCache(),
    async (url) => {
      requestedUrls.push(url);
      return new Response(html, { status: 200, headers: { "content-type": "text/html" } });
    },
  );

  const body = await response.json();
  assert.equal(requestedUrls.length, 4);
  assert.match(requestedUrls[0], /finance\.yahoo\.co\.jp\/quote\/0331418A\/history/);
  assert.equal(body.quote.price, 38000);
  assert.equal(body.quote.change, -59);
  assert.equal(body.quote.currency, "JPY");
  assert.ok(body.points.length >= 40);
});

test("maps a Japanese stock quote and history without Twelve Data", async () => {
  const requestedUrls = [];
  const response = await handleRequest(
    new Request("https://api.example/v1/jp-stocks/7203"),
    env,
    memoryCache(),
    async (url) => {
      requestedUrls.push(url);
      return upstream({ chart: { result: [{
        meta: { regularMarketPrice: 3245, chartPreviousClose: 3200, regularMarketTime: 1_700_000_000, currency: "JPY", longName: "Toyota Motor Corporation" },
        timestamp: [1_699_900_000, 1_700_000_000],
        indicators: { quote: [{ close: [3200, 3245] }] },
      }], error: null } });
    },
  );

  const body = await response.json();
  assert.match(requestedUrls[0], /7203\.T/);
  assert.match(requestedUrls[0], /interval=1d/);
  assert.equal(body.quote.symbol, "7203");
  assert.equal(body.quote.price, 3245);
  assert.equal(body.quote.change, 45);
  assert.deepEqual(body.points.map((point) => point.value), [3200, 3245]);
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
  assert.equal(kv.lastExpirationTtl, 14400);
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
