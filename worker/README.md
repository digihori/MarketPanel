# MarketPanel API Worker

Cloudflare Worker that converts Twelve Data responses into the stable JSON contract used by the Android app.

## Local verification

```sh
npm test
```

## Required secret

Configure the upstream API key before local execution or deployment:

```sh
npx wrangler secret put TWELVE_DATA_API_KEY
```

Do not commit the key to `wrangler.toml` or the Android project.

For local verification, copy `.dev.vars.example` to `.dev.vars` and replace the placeholder. `.dev.vars` is excluded from Git.

Tokyo Stock Exchange access depends on the Twelve Data subscription. The current Android demo mode remains enabled until the Worker is configured and its symbols are verified against the selected plan.
