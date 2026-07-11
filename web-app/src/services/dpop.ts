/**
 * DPoP (RFC 9449) client helper.
 *
 * Generates an ECDSA P-256 keypair once per browser, persists the *non-extractable* private
 * key in IndexedDB, and signs a DPoP proof JWS for outgoing requests.
 *
 * Enable by setting `VITE_DPOP_ENABLED=true`. When disabled, `dpopProofFor()` returns null
 * and callers should omit the DPoP header.
 *
 * The corresponding access token must carry `cnf.jkt = base64url(SHA-256(canonical JWK))`.
 * The server's DpopValidationService verifies this on every protected request.
 */

const DB_NAME = 'docuhyphen-dpop';
const STORE = 'keys';
const KEY_ID = 'dpop-signing-key';

const isEnabled = (): boolean => (import.meta.env.VITE_DPOP_ENABLED ?? 'false') === 'true';

const openDb = (): Promise<IDBDatabase> => new Promise((resolve, reject) =>
{
    const req = indexedDB.open(DB_NAME, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
});

const idbGet = async <T>(key: string): Promise<T | undefined> =>
{
    const db = await openDb();
    return new Promise((resolve, reject) =>
    {
        const tx = db.transaction(STORE, 'readonly');
        const req = tx.objectStore(STORE).get(key);
        req.onsuccess = () => resolve(req.result as T);
        req.onerror = () => reject(req.error);
    });
};

const idbPut = async (key: string, value: unknown): Promise<void> =>
{
    const db = await openDb();
    return new Promise((resolve, reject) =>
    {
        const tx = db.transaction(STORE, 'readwrite');
        tx.objectStore(STORE).put(value, key);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
    });
};

const base64UrlEncode = (bytes: ArrayBuffer | Uint8Array): string =>
{
    const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
    let s = '';
    arr.forEach(b => s += String.fromCharCode(b));
    return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

interface StoredKey
{
    privateKey: CryptoKey;
    publicJwk: JsonWebKey;
}

const ensureKey = async (): Promise<StoredKey | null> =>
{
    if (!isEnabled() || typeof crypto?.subtle === 'undefined') return null;

    const existing = await idbGet<StoredKey>(KEY_ID);
    if (existing) return existing;

    const keypair = await crypto.subtle.generateKey(
        {name: 'ECDSA', namedCurve: 'P-256'},
        false, // private key is non-extractable
        ['sign', 'verify']
    );
    const publicJwk = await crypto.subtle.exportKey('jwk', keypair.publicKey);
    const stored: StoredKey = {privateKey: keypair.privateKey, publicJwk};
    await idbPut(KEY_ID, stored);
    return stored;
};

/**
 * Returns the JWK thumbprint for binding to access tokens at sign-in time.
 * Sent to the server in a `DPoP-Bind` header during the sign-in / token request.
 */
export const dpopJwkThumbprint = async (): Promise<string | null> =>
{
    const key = await ensureKey();
    if (!key) return null;
    const canonical = JSON.stringify({
        crv: key.publicJwk.crv,
        kty: key.publicJwk.kty,
        x: key.publicJwk.x,
        y: key.publicJwk.y,
    });
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical));
    return base64UrlEncode(digest);
};

/** Build a DPoP proof JWS for a single outgoing request. Returns null if DPoP is disabled. */
export const dpopProofFor = async (httpMethod: string, requestUrl: string): Promise<string | null> =>
{
    const key = await ensureKey();
    if (!key) return null;

    const header = {
        typ: 'dpop+jwt',
        alg: 'ES256',
        jwk: {
            kty: key.publicJwk.kty,
            crv: key.publicJwk.crv,
            x: key.publicJwk.x,
            y: key.publicJwk.y,
        },
    };
    const payload = {
        htm: httpMethod.toUpperCase(),
        htu: requestUrl,
        iat: Math.floor(Date.now() / 1000),
        jti: crypto.randomUUID(),
    };

    const enc = new TextEncoder();
    const headerB64 = base64UrlEncode(enc.encode(JSON.stringify(header)));
    const payloadB64 = base64UrlEncode(enc.encode(JSON.stringify(payload)));
    const signingInput = `${headerB64}.${payloadB64}`;

    const sigBuffer = await crypto.subtle.sign(
        {name: 'ECDSA', hash: 'SHA-256'},
        key.privateKey,
        enc.encode(signingInput)
    );
    const sigB64 = base64UrlEncode(sigBuffer);
    return `${signingInput}.${sigB64}`;
};

type DpopHeaderBag = {
    [key: string]: unknown;
    set?: (name: string, value: string) => void;
};

/** Wire into the axios request interceptor by adding a DPoP header to every request. */
export const attachDpopToAxiosConfig = async (config: {method?: string; url?: string; baseURL?: string; headers?: DpopHeaderBag}): Promise<void> =>
{
    if (!isEnabled()) return;
    const method = config.method ?? 'GET';
    const url = (config.baseURL ?? '') + (config.url ?? '');
    const proof = await dpopProofFor(method, url);
    if (proof && config.headers)
    {
        if (typeof config.headers.set === 'function')
        {
            config.headers.set('DPoP', proof);
        }
        else
        {
            config.headers['DPoP'] = proof;
        }
    }
};
