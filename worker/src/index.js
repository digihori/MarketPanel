const MARKET_SYMBOLS = {
  // Direct index symbols return 404 on the Twelve Data Basic plan.
  // Use US-listed ETF proxies that are available on the free plan.
  NIKKEI225: "EWJ",
  SP500: "VOO",
  DOW30: "DIA",
  NASDAQ100: "QQQ",
  USDJPY: "USD/JPY",
};

const MARKET_NAMES = {
  NIKKEI225: "日本株参考（EWJ）",
  SP500: "S&P 500参考（VOO）",
  DOW30: "NYダウ参考（DIA）",
  NASDAQ100: "NASDAQ-100参考（QQQ）",
  VIX: "VIX指数",
  USDJPY: "米ドル／円",
};

const VIX_LAST_GOOD_KEY = "marketpanel:last-good:vix";

const FUND_DEFINITIONS = {
  EMAXIS_ALL_COUNTRY: { code: "0331418A", name: "eMAXIS Slim 全世界株式（オール・カントリー）" },
  EMAXIS_SP500: { code: "03311187", name: "eMAXIS Slim 米国株式（S&P500）" },
  IFREE_FANG_PLUS: { code: "04311181", name: "iFreeNEXT FANG+インデックス" },
  SBI_S_SCHD_4X: { code: "8931224C", name: "SBI・S・米国高配当株式ファンド（年4回決算型）" },
  TRACERS_NIKKEI_HD50: { code: "02313241", name: "Tracers 日経平均高配当株50インデックス（奇数月分配型）" },
};

export default {
  async fetch(request, env, context) {
    return handleRequest(request, env, caches.default, fetch, context);
  },
};

export async function handleRequest(request, env, cache, fetchImpl, context = null) {
  if (request.method !== "GET") return json({ error: "method_not_allowed" }, 405);
  if (!env.TWELVE_DATA_API_KEY) return json({ error: "server_not_configured" }, 503);

  const url = new URL(request.url);
  const parts = url.pathname.split("/").filter(Boolean);
  const historyCacheVersion = parts[0] === "v1" && parts[1] === "markets"
    ? "history-v4"
    : parts[0] === "v1" && parts[1] === "jp-stocks"
      ? "history-v4"
      : parts[0] === "v1" && parts[1] === "funds"
      ? "history-v2"
      : "";
  const cacheUrl = new URL(request.url);
  if (historyCacheVersion) cacheUrl.searchParams.set("__marketpanel_cache", historyCacheVersion);
  const cacheRequest = new Request(cacheUrl.toString(), request);
  const cached = await cache.match(cacheRequest);
  if (cached) return withCacheHeader(cached, "HIT");

  try {
    const kvKey = `marketpanel:${historyCacheVersion}:${url.pathname}${url.search}`;
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
    } else if (parts[1] === "funds" && parts[2]) {
      response = await fundResponse(decodeURIComponent(parts[2]), env, fetchImpl);
    } else if (parts[1] === "jp-stocks" && parts[2]) {
      response = await japanStockResponse(decodeURIComponent(parts[2]), env, fetchImpl);
    } else {
      return json({ error: "not_found" }, 404);
    }

    const writes = [cache.put(cacheRequest, response.clone())];
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
  if (parts[1] === "funds") return 24 * 60 * 60;
  if (parts[1] === "quotes" || parts[1] === "markets" || parts[1] === "jp-stocks") return 4 * 60 * 60;
  if (parts[1] === "usage") return 60 * 60;
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
    3600,
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
  if (id === "VIX") return vixMarketResponse(env, fetchImpl);
  if (id === "NIKKEI225") {
    try {
      return await yahooIndexResponse("NIKKEI225", "%5EN225", "日経平均株価", "JPY", fetchImpl);
    } catch {
      // Fall back to the free-plan EWJ proxy below when Yahoo is unavailable.
    }
  }
  const symbol = MARKET_SYMBOLS[id];
  if (!symbol) return json({ error: "unknown_market" }, 404);
  const [quote, series] = await Promise.all([
    upstreamJson("quote", { symbol }, env, fetchImpl),
    upstreamJson("time_series", { symbol, interval: "1week", outputsize: "52", order: "ASC" }, env, fetchImpl),
  ]);
  const mappedQuote = mapQuote(quote, id);
  mappedQuote.name = MARKET_NAMES[id] || mappedQuote.name;
  return apiJson(
    {
      id,
      quote: mappedQuote,
      points: mapPoints(series.values),
    },
    env,
  );
}

async function fundResponse(id, env, fetchImpl) {
  const definition = FUND_DEFINITIONS[id] || (/^[0-9A-Z]{8}$/.test(id) ? { code: id, name: id } : null);
  if (!definition) return json({ error: "unknown_fund" }, 404);
  const lastGoodKey = `marketpanel:last-good:fund:${id}`;
  try {
    const baseUrl = `https://finance.yahoo.co.jp/quote/${encodeURIComponent(definition.code)}/history`;
    const { from, to } = oneYearDateRange();
    const urls = [
      baseUrl,
      ...[1, 2, 3].map((page) => `${baseUrl}?from=${from}&to=${to}&timeFrame=w&page=${page}`),
    ];
    const histories = await Promise.all(urls.map(async (url) => {
      const upstream = await fetchImpl(url, {
        headers: {
          Accept: "text/html",
          "Accept-Language": "ja-JP,ja;q=0.9",
          "User-Agent": "Mozilla/5.0 MarketPanel/1.0",
        },
      });
      if (!upstream.ok) throw new UpstreamError(`Fund page returned HTTP ${upstream.status}`, 502);
      return parseYahooFundHistory(await upstream.text());
    }));
    const latest = histories[0][0];
    const weeklyItems = [...new Map(histories.slice(1).flat()
      .map((item) => [item.timestamp, item])).values()]
      .sort((a, b) => a.timestamp - b.timestamp);
    if (!latest || weeklyItems.length < 2) throw new UpstreamError("Fund page returned insufficient history", 502);
    const previousPrice = latest.price - latest.change;
    const response = json({
      id,
      quote: {
        symbol: definition.code,
        name: definition.name,
        exchange: "Yahoo!ファイナンス",
        currency: "JPY",
        price: latest.price,
        change: latest.change,
        changePercent: previousPrice === 0 ? 0 : latest.change / previousPrice * 100,
        updatedAt: latest.timestamp,
      },
      points: weeklyItems.map(({ timestamp, price }) => ({ timestamp, value: price })),
    }, 200, 300);
    if (env.MARKET_CACHE) await writeKvResponse(env.MARKET_CACHE, lastGoodKey, response.clone(), 30 * 24 * 60 * 60);
    return response;
  } catch (error) {
    const stale = await readKvResponse(env.MARKET_CACHE, lastGoodKey);
    if (stale) return stale;
    throw error;
  }
}

function oneYearDateRange(now = new Date()) {
  const to = new Date(now);
  const from = new Date(now);
  from.setUTCFullYear(from.getUTCFullYear() - 1);
  const format = (value) => `${value.getUTCFullYear()}${String(value.getUTCMonth() + 1).padStart(2, "0")}${String(value.getUTCDate()).padStart(2, "0")}`;
  return { from: format(from), to: format(to) };
}

function parseYahooFundHistory(html) {
  const match = html.match(/historyTable\\":\{\\"items\\":(\[.*?\]),\\"hasNext/);
  if (!match) throw new UpstreamError("Fund history data was not found", 502);
  let rows;
  try {
    rows = JSON.parse(match[1].replaceAll('\\"', '"'));
  } catch {
    throw new UpstreamError("Fund history data could not be parsed", 502);
  }
  return rows.flatMap((row) => {
    const [year, month, day] = String(row.date).split("/").map(Number);
    const timestamp = Math.floor(Date.UTC(year, month - 1, day) / 1000);
    const price = Number(String(row.price).replaceAll(",", ""));
    const change = Number(String(row.priceChange).replaceAll(",", ""));
    return Number.isFinite(timestamp) && Number.isFinite(price) && Number.isFinite(change) && price > 0
      ? [{ timestamp, price, change }]
      : [];
  });
}

async function japanStockResponse(requestedSymbol, env, fetchImpl) {
  const normalized = requestedSymbol.toUpperCase().endsWith(".T")
    ? requestedSymbol.toUpperCase()
    : `${requestedSymbol.toUpperCase()}.T`;
  if (!/^[0-9A-Z]{4,6}\.T$/.test(normalized)) return json({ error: "invalid_japan_symbol" }, 400);
  const lastGoodKey = `marketpanel:last-good:jp-stock:${normalized}`;
  try {
    const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(normalized)}?range=1y&interval=1d&events=history`;
    const upstream = await fetchImpl(url, { headers: { Accept: "application/json", "User-Agent": "MarketPanel/1.0" } });
    const body = await upstream.json();
    const result = body?.chart?.result?.[0];
    if (!upstream.ok || !result || body?.chart?.error) {
      throw new UpstreamError(body?.chart?.error?.description || `Yahoo returned HTTP ${upstream.status}`, 502);
    }
    const meta = result.meta || {};
    const timestamps = result.timestamp || [];
    const closes = result.indicators?.quote?.[0]?.close || [];
    const points = timestamps.flatMap((timestamp, index) => {
      const value = Number(closes[index]);
      return Number.isFinite(value) && value > 0 ? [{ timestamp: Number(timestamp), value }] : [];
    });
    if (points.length < 2) throw new UpstreamError("Yahoo returned insufficient Japan stock history", 502);
    const price = number(meta.regularMarketPrice ?? points.at(-1).value);
    const previous = points.at(-2).value;
    const change = price - previous;
    const response = json({
      id: normalized,
      quote: {
        symbol: normalized.replace(/\.T$/, ""),
        name: meta.longName || meta.shortName || normalized,
        exchange: meta.fullExchangeName || "東京証券取引所",
        currency: meta.currency || "JPY",
        price,
        change,
        changePercent: previous === 0 ? 0 : change / previous * 100,
        updatedAt: Number(meta.regularMarketTime) || points.at(-1).timestamp,
      },
      points: weeklyLastPoints(points),
    }, 200, 300);
    if (env.MARKET_CACHE) await writeKvResponse(env.MARKET_CACHE, lastGoodKey, response.clone(), 30 * 24 * 60 * 60);
    return response;
  } catch (error) {
    const stale = await readKvResponse(env.MARKET_CACHE, lastGoodKey);
    if (stale) return stale;
    throw error;
  }
}

async function vixMarketResponse(env, fetchImpl) {
  const failures = [];
  try {
    const response = await yahooVixResponse(fetchImpl);
    await storeLastGoodVix(env.MARKET_CACHE, response.clone());
    return response;
  } catch (error) {
    failures.push(error);
  }
  try {
    const response = await cboeVixResponse(fetchImpl);
    await storeLastGoodVix(env.MARKET_CACHE, response.clone());
    return response;
  } catch (error) {
    failures.push(error);
  }
  const stale = await readKvResponse(env.MARKET_CACHE, VIX_LAST_GOOD_KEY);
  if (stale) return stale;
  const messages = failures.map((error) => error instanceof Error ? error.message : String(error));
  throw new UpstreamError(`VIX providers failed: ${messages.join("; ")}`, 502);
}

async function yahooVixResponse(fetchImpl) {
  const url = "https://query1.finance.yahoo.com/v8/finance/chart/%5EVIX?range=1y&interval=1d&events=history";
  const response = await fetchImpl(url, { headers: { Accept: "application/json", "User-Agent": "MarketPanel/1.0" } });
  const body = await response.json();
  const result = body?.chart?.result?.[0];
  if (!response.ok || !result || body?.chart?.error) {
    throw new UpstreamError(body?.chart?.error?.description || `Yahoo returned HTTP ${response.status}`, 502);
  }
  const meta = result.meta || {};
  const timestamps = result.timestamp || [];
  const closes = result.indicators?.quote?.[0]?.close || [];
  const points = timestamps.flatMap((timestamp, index) => {
    const value = Number(closes[index]);
    return Number.isFinite(value) && value > 0 ? [{ timestamp: Number(timestamp), value }] : [];
  });
  if (points.length < 2) throw new UpstreamError("Yahoo returned insufficient VIX history", 502);
  const price = number(meta.regularMarketPrice ?? points.at(-1).value);
  const previous = points.at(-2).value;
  const change = price - previous;
  return json({
    id: "VIX",
    quote: {
      symbol: "VIX",
      name: "VIX指数 • 遅延値",
      exchange: meta.fullExchangeName || meta.exchangeName || "Cboe",
      currency: "PCT",
      price,
      change,
      changePercent: previous === 0 ? 0 : change / previous * 100,
      updatedAt: Number(meta.regularMarketTime) || points.at(-1).timestamp,
    },
    points: weeklyLastPoints(points),
  }, 200, 300);
}

async function yahooIndexResponse(id, encodedSymbol, name, currency, fetchImpl) {
  const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodedSymbol}?range=1y&interval=1d&events=history`;
  const response = await fetchImpl(url, { headers: { Accept: "application/json", "User-Agent": "MarketPanel/1.0" } });
  const body = await response.json();
  const result = body?.chart?.result?.[0];
  if (!response.ok || !result || body?.chart?.error) {
    throw new UpstreamError(body?.chart?.error?.description || `Yahoo returned HTTP ${response.status}`, 502);
  }
  const meta = result.meta || {};
  const timestamps = result.timestamp || [];
  const closes = result.indicators?.quote?.[0]?.close || [];
  const points = timestamps.flatMap((timestamp, index) => {
    const value = Number(closes[index]);
    return Number.isFinite(value) && value > 0 ? [{ timestamp: Number(timestamp), value }] : [];
  });
  if (points.length < 2) throw new UpstreamError("Yahoo returned insufficient index history", 502);
  const price = number(meta.regularMarketPrice ?? points.at(-1).value);
  const previous = points.at(-2).value;
  const change = price - previous;
  return json({
    id,
    quote: {
      symbol: id,
      name: `${name} • 遅延値`,
      exchange: meta.fullExchangeName || meta.exchangeName || "Yahoo Finance",
      currency,
      price,
      change,
      changePercent: previous === 0 ? 0 : change / previous * 100,
      updatedAt: Number(meta.regularMarketTime) || points.at(-1).timestamp,
    },
    points: weeklyLastPoints(points),
  }, 200, 300);
}

async function cboeVixResponse(fetchImpl) {
  const url = "https://cdn.cboe.com/api/global/us_indices/daily_prices/VIX_History.csv";
  const response = await fetchImpl(url, { headers: { Accept: "text/csv" } });
  if (!response.ok) throw new UpstreamError(`Cboe returned HTTP ${response.status}`, 502);
  const rows = (await response.text()).trim().split(/\r?\n/).slice(1).flatMap((line) => {
    const [date, , , , close] = line.split(",").map((value) => value.trim());
    const [month, day, year] = date.split("/").map(Number);
    const timestamp = Math.floor(Date.UTC(year, month - 1, day) / 1000);
    const value = Number(close);
    return Number.isFinite(timestamp) && Number.isFinite(value) ? [{ timestamp, value }] : [];
  });
  if (rows.length < 2) throw new UpstreamError("Cboe returned insufficient VIX history", 502);
  const latest = rows.at(-1);
  const previous = rows.at(-2);
  const oneYearAgo = latest.timestamp - 366 * 24 * 60 * 60;
  const oneYearRows = rows.filter((row) => row.timestamp >= oneYearAgo);
  const weeklyRows = weeklyLastPoints(oneYearRows);
  const change = latest.value - previous.value;
  return json({
    id: "VIX",
    quote: {
      symbol: "VIX",
      name: "VIX指数 • 前営業日終値",
      exchange: "Cboe",
      currency: "PCT",
      price: latest.value,
      change,
      changePercent: previous.value === 0 ? 0 : change / previous.value * 100,
      updatedAt: latest.timestamp,
    },
    points: weeklyRows,
  }, 200, 300);
}

function weeklyLastPoints(points) {
  const weeks = new Map();
  for (const point of points) {
    const date = new Date(point.timestamp * 1_000);
    const daysSinceMonday = (date.getUTCDay() + 6) % 7;
    const weekStart = point.timestamp - daysSinceMonday * 24 * 60 * 60;
    weeks.set(weekStart, point);
  }
  return [...weeks.values()].sort((a, b) => a.timestamp - b.timestamp);
}

async function storeLastGoodVix(kv, response) {
  if (!kv) return;
  await writeKvResponse(kv, VIX_LAST_GOOD_KEY, response, 30 * 24 * 60 * 60);
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
