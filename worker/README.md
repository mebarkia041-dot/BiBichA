# BiBichA AI backend

The GitHub Pages app is static. This Worker securely calls the OpenAI API. The API key must stay in Cloudflare Secrets; never paste it into GitHub, `index.html`, or `api-config.js`.

## Deploy from a phone using the Cloudflare dashboard

1. Open https://dash.cloudflare.com/ and sign in.
2. Open **Workers & Pages** → **Create** → **Create Worker**. Name it `bibicha-ai`.
3. Open the new Worker → **Edit code**. Replace the editor contents with the complete code in `worker/src/index.js`, then **Deploy**.
4. Open **Settings** → **Variables and Secrets**. Add these plain-text variables:
   - `ALLOWED_ORIGIN` = `https://mebarkia041-dot.github.io`
   - `OPENAI_MODEL` = `gpt-4.1-mini`
5. Under the same section add a **Secret** named `OPENAI_API_KEY`. Paste your own OpenAI API key into the secret field and save. Never send this key to anyone or commit it to GitHub.
6. Copy the Worker URL shown by Cloudflare. The chat endpoint is that URL followed by `/chat`.
7. In this GitHub repository open `api-config.js` → Edit (pencil). Set `window.BIBICHA_API_URL` to the full `https://...workers.dev/chat` URL, then commit the change.
8. Wait for the GitHub Pages deployment. Open the app, refresh it, and send a test message.

## Verify

Open the Worker URL followed by `/health`. It should return JSON with `"service":"BiBichA AI"` and `"configured":true`. This only confirms that the secret exists; a real chat test confirms API access and billing are working.

A valid OpenAI API key with API billing/credits is required. The app's speech features use browser speech recognition and speech synthesis; availability and the Arabic voice depend on the phone/browser. Saved memories are currently stored locally on this phone and are sent to the AI only when you chat.
