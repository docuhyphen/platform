# Demo Exchange Generator

This tool bootstraps demo data end-to-end:

1. Sign-up initiation
2. Sign-up completion (OTP `123456`)
3. Sign-in initiation
4. Sign-in completion (OTP `123456`)
5. Person registration
6. Organization registration
7. Sharing session creation

## What it creates

- 5 industries
- 20 sessions per industry by default (configurable)
- 3 to 6 documents per session (randomized)

## Files

- `apis/demo-exchange-generator.html`
- `apis/demo-exchange-generator.js`

## Usage

1. Start your API server on `http://localhost:8080` (or change Base URL in the page).
2. Open `apis/demo-exchange-generator.html` in your browser.
3. Set an email domain and password for generated accounts.
4. Click **Generate Demo Sessions**.

## Notes

- The generator uses best-effort mode: it continues even if some requests fail.
- It logs request outcomes and a failure summary at the end.
- Session payload shape follows examples in `apis/exchange-api.http`.
- One account and one organization are created per industry with generated names.

