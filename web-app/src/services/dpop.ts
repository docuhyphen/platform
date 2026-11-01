
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
