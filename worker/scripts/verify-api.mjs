import { readFile } from "node:fs/promises";

const vars = Object.fromEntries(
  (await readFile(new URL("../.dev.vars", import.meta.url), "utf8"))
    .split(/\r?\n/)
    .filter((line) => line.trim() && !line.trim().startsWith("#"))
    .map((line) => {
      const separator = line.indexOf("=");
      return [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
    }),
);

const apiKey = vars.TWELVE_DATA_API_KEY;
if (!apiKey || apiKey === "replace_with_your_api_key") {
  throw new Error("TWELVE_DATA_API_KEY is not configured in worker/.dev.vars");
}

const headers = { Authorization: `apikey ${apiKey}`, Accept: "application/json" };
const requests = [
  request("quote", "https://api.twelvedata.com/quote?symbol=IBM", (body) => ({
    symbol: body.symbol,
    name: body.name,
    exchange: body.exchange,
    currency: body.currency,
    close: body.close,
    change: body.change,
    percentChange: body.percent_change,
    timestamp: body.timestamp,
  })),
  request("weekly", "https://api.twelvedata.com/time_series?symbol=IBM&interval=1week&outputsize=60&order=ASC", (body) => ({
    symbol: body.meta?.symbol,
    interval: body.meta?.interval,
    pointCount: body.values?.length ?? 0,
    firstDate: body.values?.[0]?.datetime,
    lastDate: body.values?.at(-1)?.datetime,
  })),
  request("usage", "https://api.twelvedata.com/api_usage", summarizeUsage),
];

const results = await Promise.all(requests);
console.log(JSON.stringify(results, null, 2));

async function request(name, url, summarize) {
  const response = await fetch(url, { headers });
  const body = await response.json();
  if (!response.ok || body.status === "error") {
    return { name, ok: false, status: response.status, error: body.message ?? "Request failed" };
  }
  return {
    name,
    ok: true,
    status: response.status,
    creditsUsedHeader: response.headers.get("api-credits-used"),
    creditsLeftHeader: response.headers.get("api-credits-left"),
    data: summarize(body),
  };
}

function summarizeUsage(body) {
  return {
    currentUsage: body.current_usage,
    planLimit: body.plan_limit,
    dailyUsage: body.daily_usage,
    dailyLimit: body.daily_limit,
    plan: body.plan,
  };
}
