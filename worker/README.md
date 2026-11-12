# BiBichA AI backend

The GitHub Pages site is static. This Worker keeps the OpenAI API key on the server; never put the key in `index.html`, `api-config.js`, or a public GitHub file.

## Deploy

1. Create a Cloudflare account and install Wrangler on a computer, or use the Cloudflare dashboard's Worker editor.
2. Create a Worker named `bibicha-ai` and paste the code from `src/index.js`.
3. Add Worker variables:
   - `ALLOWED_ORIGIN` = `https://mebarkia041-dot.github.io`
   - `OPENAI_MODEL` = `gpt-4.1-mini`
4. Add a secret named `OPENAI_API_KEY` in Worker Settings > Variables and Secrets. Do not share it in chat or commit it to GitHub.
5. Deploy the Worker. Its endpoint will look like `https://bibicha-ai.<your-subdomain>.workers.dev/chat`.
6. Put that full endpoint in `api-config.js` as `window.BIBICHA_API_URL`, then commit the file.

The Worker only accepts requests from the configured GitHub Pages origin. A valid OpenAI API key and API billing/credits are required. Until deployment and configuration are complete, the app intentionally keeps its demo replies.
