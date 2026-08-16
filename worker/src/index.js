const MARKET_SYMBOLS = {
  NIKKEI225: "N225",
  SP500: "SPX",
  USDJPY: "USD/JPY",
};

export default {
  async fetch(request, env, context) {
    return handleRequest(request, env, caches.default, fetch, context);
  },
};

export async function handleRequest(request, env, cache, fetchImpl, context = null) {
  if (request.method !== "GET") return json({ error: "method_not_allowed" }, 405);
  if (!env.TWELVE_DATA_API_KEY) return json({ error: "server_not_configured" }, 503);

  const cached = await cache.match(request);
  if (cached) return withCacheHeader(cached, "HIT");

  try {
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);
    const kvKey = `marketpanel:${url.pathname}${url.search}`;
    const kvCached = await readKvResponse(env.MARKET_CACHE, kvKey);
    if (kvCached) return withCacheHeader(kvCached, "KV");
    let response;
    if (parts[0] !== "v1") return json({ error: "not_found" }, 404);

    if (parts[1] === "usage" && parts.length === 2) {
      response = await usageResponse(env, fetchImpl);
    } else if (parts[1] === "quotes" && parts[2]) {
      response = await quoteResponse(decodeURIComponent(parts[2]), env, fetchImpl);
    } else if (parts[1] === "charts" && parts[2]) {
      response = await chartResponse(decodeURIComponent(parts[2]), url.searchParams, env, fetchImpl);
    } else if (parts[1] === "markets" && parts[2]) {
      response = await marketResponse(decodeURIComponent(parts[2]), env, fetchImpl);
    } else {
      return json({ error: "not_found" }, 404);
    }

    const writes = [cache.put(request, response.clone())];
    if (response.ok && env.MARKET_CACHE) {
      writes.push(writeKvResponse(env.MARKET_CACHE, kvKey, response.clone(), kvTtl(parts)));
    }
    const cacheWrite = Promise.all(writes);
    if (context?.waitUntil) context.waitUntil(cacheWrite);
    else await cacheWrite;
    return withCacheHeader(response, "MISS");
  } catch (error) {
    const status = error instanceof UpstreamError ? error.status : 502;
    return json(
      { error: "upstream_error", message: error instanceof Error ? error.message : "Unknown error" },
      status,
    );
  }
}

function kvTtl(parts) {
  if (parts[1] === "charts") return 24 * 60 * 60;
  if (parts[1] === "quotes" || parts[1] === "markets") return 2 * 60 * 60;
  if (parts[1] === "usage") return 15 * 60;
  return 5 * 60;
}

async function readKvResponse(kv, key) {
  if (!kv) return null;
  const stored = await kv.get(key, "json");
  if (!stored) return null;
  return new Response(stored.body, {
    status: stored.status,
    headers: stored.headers,
  });
}

async function writeKvResponse(kv, key, response, expirationTtl) {
  const headers = Object.fromEntries(response.headers.entries());
  await kv.put(
    key,
    JSON.stringify({ status: response.status, headers, body: await response.text() }),
    { expirationTtl },
  );
}

async function usageResponse(env, fetchImpl) {
  const upstream = await upstreamJson("api_usage", {}, env, fetchImpl);
  return json(
    {
      dailyUsage: Number(upstream.daily_usage) || 0,
      dailyLimit: 800,
      currentUsage: Number(upstream.current_usage) || 0,
      minuteLimit: Number(upstream.plan_limit) || 8,
    },
    200,
    900,
  );
}

async function quoteResponse(symbol, env, fetchImpl) {
  const upstream = await upstreamJson("quote", { symbol: upstreamSymbol(symbol) }, env, fetchImpl);
  return apiJson(mapQuote(upstream, symbol), env);
}

async function chartResponse(symbol, params, env, fetchImpl) {
  const range = params.get("range") || "1y";
  const interval = normalizeInterval(params.get("interval") || "1week");
  const upstream = await upstreamJson(
    "time_series",
    { symbol: upstreamSymbol(symbol), interval, outputsize: outputSize(range, interval), order: "ASC" },
    env,
    fetchImpl,
  );
  return apiJson(
    {
      symbol,
      range,
      interval,
      points: mapPoints(upstream.values),
    },
    env,
  );
}

async function marketResponse(id, env, fetchImpl) {
  const symbol = MARKET_SYMBOLS[id];
  if (!symbol) return json({ error: "unknown_market" }, 404);
  const [quote, series] = await Promise.all([
    upstreamJson("quote", { symbol }, env, fetchImpl),
    upstreamJson("time_series", { symbol, interval: "1day", outputsize: "60", order: "ASC" }, env, fetchImpl),
  ]);
  return apiJson(
    {
      id,
      quote: mapQuote(quote, id),
      points: mapPoints(series.values),
    },
    env,
  );
}

async function upstreamJson(endpoint, params, env, fetchImpl) {
  const url = new URL(endpoint, `${env.UPSTREAM_BASE_URL || "https://api.twelvedata.com"}/`);
  for (const [key, value] of Object.entries(params)) url.searchParams.set(key, String(value));
  url.searchParams.set("apikey", env.TWELVE_DATA_API_KEY);
  const response = await fetchImpl(url.toString(), { headers: { Accept: "application/json" } });
  const body = await response.json();
  if (!response.ok || body.status === "error" || body.code) {
    throw new UpstreamError(
      body.message || `Upstream returned HTTP ${response.status}`,
      Number(body.code) || response.status || 502,
    );
  }
  return body;
}

function mapQuote(value, requestedSymbol) {
  return {
    symbol: requestedSymbol,
    name: value.name || value.symbol || requestedSymbol,
    exchange: value.exchange || value.currency_base || "Market",
    currency: value.currency || value.currency_quote || "USD",
    price: number(value.close ?? value.price),
    change: number(value.change),
    changePercent: number(value.percent_change),
    updatedAt: Number(value.timestamp) || Math.floor(Date.now() / 1000),
  };
}

function mapPoints(values = []) {
  return [...values].reverse().sort((a, b) => a.datetime.localeCompare(b.datetime)).map((value) => ({
    timestamp: Math.floor(Date.parse(value.datetime.replace(" ", "T") + "Z") / 1000),
    value: number(value.close),
  }));
}

function upstreamSymbol(symbol) {
  if (/^\d{4}$/.test(symbol)) return `${symbol}:JPX`;
  return symbol;
}

function normalizeInterval(interval) {
  const values = { "5min": "5min", "1day": "1day", "1wk": "1week", "1week": "1week" };
  return values[interval] || "1week";
}

function outputSize(range, interval) {
  if (range === "1d" && interval === "5min") return "100";
  return { "3mo": "70", "6mo": "140", "1y": "60", "3y": "160" }[range] || "60";
}

function number(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) throw new UpstreamError("Invalid numeric value from upstream", 502);
  return parsed;
}

function apiJson(body, env) {
  return json(body, 200, Number(env.CACHE_TTL_SECONDS || 300));
}

function json(body, status = 200, maxAge = 0) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": maxAge > 0 ? `public, max-age=${maxAge}` : "no-store",
    },
  });
}

function withCacheHeader(response, value) {
  const copy = new Response(response.body, response);
  copy.headers.set("x-marketpanel-cache", value);
  return copy;
}

class UpstreamError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status >= 400 && status < 600 ? status : 502;
  }
}
