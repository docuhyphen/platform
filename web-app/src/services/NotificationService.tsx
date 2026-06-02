import {jwtDecode} from "jwt-decode";
import {NotificationDto} from '../app/models/models';

/**
 * Realtime client for the per-userSession WebSocket at /realtime/{userSessionId}.
 *
 *  - Keyed by the access token's `session_id` claim so two devices for the same user have
 *    independent sockets.
 *  - Indefinite reconnect with exponential backoff. The old 5-attempt cap dropped users
 *    permanently after a transient network blip; we never want that for the auth-event
 *    channel.
 *  - Heartbeat ping every 25s; the server replies PONG and touches the UserSession.
 *  - Typed message dispatch via per-type handler sets, plus a wildcard `*` for the
 *    NotificationContext that wants every NOTIFICATION envelope.
 *
 * Backwards compatibility: NotificationContext still imports `notificationService` from
 * this module and calls `connect(appUserId)` / `addMessageHandler`. The legacy methods are
 * preserved,  internally they resolve the userSessionId from the access token.
 */

export type RealtimeMessageType =
    | 'PING'
    | 'PONG'
    | 'SUBSCRIBE_SHARING_SESSION'
    | 'UNSUBSCRIBE_SHARING_SESSION'
    | 'SESSION_REVOKED'
    | 'SESSION_CREATED'
    | 'SESSION_REMOVED'
    | 'PASSWORD_CHANGED'
    | 'SIGNED_OUT_OTHER_DEVICE'
    | 'NOTIFICATION'
    | 'PRESENCE_UPDATE'
    | 'SHARING_VIEWERS'
    | 'SHARING_SESSION_DOCUMENT_ADDED'
    | 'SHARING_SESSION_DOCUMENT_REMOVED'
    | 'SHARING_SESSION_DOCUMENT_UPDATED'
    | 'SHARING_SESSION_STATUS_CHANGED'
    | 'WORKFLOW_STEP_ASSIGNED'
    | 'WORKFLOW_ESCALATED'
    | 'SESSION_ACTIVATED'
    | 'SESSION_REJECTED'
    | 'ERROR'
    | 'WELCOME';

export interface RealtimeSessionInfo
{
    sessionId: string;
    deviceName?: string;
    ipAddress?: string;
    userAgent?: string;
    createdDate: string;
    lastSeenAt: string;
    expiresAt?: string;
    isCurrent?: boolean;
}

export interface RealtimeMessage
{
    type: RealtimeMessageType;
    sharingSessionId?: string;
    notification?: NotificationDto;
    userId?: string;
    userSessionId?: string;
    online?: boolean;
    viewerUserIds?: string[];
    reason?: string;
    code?: string;
    message?: string;
    serverTime?: number;
    session?: RealtimeSessionInfo;
    documentId?: string;
    status?: string;
}

type TypedHandler = (msg: RealtimeMessage) => void;
type LegacyNotificationHandler = (notification: NotificationDto) => void;

interface AccessTokenClaims
{
    sub?: string;
    session_id?: string;
    exp?: number;
}

const HEARTBEAT_INTERVAL_MS = 25_000;
const RECONNECT_BASE_MS = 1_000;
const RECONNECT_CAP_MS = 30_000;
const CLOSE_CODE_SESSION_REVOKED = 4001;
const CLOSE_CODE_AUTH_FAILED = 4401;

class RealtimeService
{
    private socket: WebSocket | null = null;
    private userSessionId: string | null = null;
    private reconnectAttempts = 0;
    private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
    private explicitlyDisconnected = false;

    private typedHandlers = new Map<RealtimeMessageType | '*', Set<TypedHandler>>();
    private legacyNotificationHandlers = new Set<LegacyNotificationHandler>();

    /**
     * Connect using the current access token in sessionStorage. The userSessionId is
     * derived from the token's `session_id` claim,  callers don't need to pass anything,
     * but for backwards compat we still accept an unused appUserId argument.
     */
    connect(appUserId?: string): void
    {
        void appUserId;
        console.info('[Realtime] connect() called');
        const token = sessionStorage.getItem('accessToken');
        if (!token)
        {
            console.warn('[Realtime] no access token; deferring connect');
            return;
        }

        const claims = decodeToken(token);
        if (isTokenExpired(claims))
        {
            console.warn('[Realtime] access token expired; skipping connect until refresh');
            return;
        }

        const sessionId = claims?.session_id;
        if (!sessionId)
        {
            console.warn('[Realtime] access token missing session_id claim', claims);
            return;
        }

        this.explicitlyDisconnected = false;

        // If a socket already exists for the same userSession, leave it alone.
        if (this.socket && this.userSessionId === sessionId && this.socket.readyState <= WebSocket.OPEN)
        {
            console.info('[Realtime] already connected for this session, skipping');
            return;
        }

        this.teardownSocket();
        this.userSessionId = sessionId;
        this.openSocket(token, sessionId);
    }

    disconnect(): void
    {
        this.explicitlyDisconnected = true;
        if (this.reconnectTimer)
        {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
        this.teardownSocket();
        this.userSessionId = null;
        this.reconnectAttempts = 0;
    }

    /**
     * Register a typed handler. Pass `'*'` to receive every message.
     * Returns an unsubscribe function.
     */
    on(type: RealtimeMessageType | '*', handler: TypedHandler): () => void
    {
        let set = this.typedHandlers.get(type);
        if (!set)
        {
            set = new Set();
            this.typedHandlers.set(type, set);
        }
        set.add(handler);
        return () => set!.delete(handler);
    }

    /**
     * Legacy compatibility: NotificationContext still uses this entry point. Wraps a
     * NOTIFICATION-only typed handler.
     */
    addMessageHandler(handler: LegacyNotificationHandler): () => void
    {
        this.legacyNotificationHandlers.add(handler);
        return () => { this.legacyNotificationHandlers.delete(handler); };
    }

    /** Send a typed envelope. No-op if disconnected. */
    send(message: RealtimeMessage): void
    {
        if (this.socket && this.socket.readyState === WebSocket.OPEN)
        {
            this.socket.send(JSON.stringify(message));
        }
    }

    subscribeToSharingSession(sharingSessionId: string): void
    {
        this.send({type: 'SUBSCRIBE_SHARING_SESSION', sharingSessionId});
    }

    unsubscribeFromSharingSession(sharingSessionId: string): void
    {
        this.send({type: 'UNSUBSCRIBE_SHARING_SESSION', sharingSessionId});
    }

    private openSocket(token: string, userSessionId: string): void
    {
        const apiBase = import.meta.env.VITE_API_BASE_URL as string | undefined;
        // If env var is set, derive ws/wss from its scheme; otherwise default to localhost dev.
        const wsBase = apiBase
            ? apiBase.replace(/^https:\/\//, 'wss://').replace(/^http:\/\//, 'ws://')
            : 'ws://localhost:8080';
        const url = `${wsBase}/realtime/${encodeURIComponent(userSessionId)}?token=${encodeURIComponent(token)}`;
        console.info('[Realtime] connecting', {wsBase, userSessionId});

        let socket: WebSocket;
        try
        {
            socket = new WebSocket(url);
        }
        catch (err)
        {
            console.error('Realtime: failed to construct WebSocket', err);
            this.scheduleReconnect();
            return;
        }

        this.socket = socket;

        socket.onopen = () =>
        {
            console.info('[Realtime] open', {userSessionId});
            this.reconnectAttempts = 0;
            this.startHeartbeat();
        };

        socket.onmessage = (event) =>
        {
            try
            {
                const msg = JSON.parse(event.data) as RealtimeMessage;
                console.info('[Realtime] message', msg.type, msg);
                this.dispatch(msg);
            }
            catch (err)
            {
                console.warn('[Realtime] bad payload', err, event.data);
            }
        };

        socket.onclose = (event) =>
        {
            console.info('[Realtime] close', {code: event.code, reason: event.reason, wasClean: event.wasClean});
            this.stopHeartbeat();
            this.socket = null;
            // Auth failed at handshake,  don't reconnect indefinitely; let AuthContext drive
            // the next attempt via a token refresh.
            if (event.code === CLOSE_CODE_AUTH_FAILED)
            {
                console.warn('[Realtime] auth failed, not reconnecting until token refresh');
                return;
            }
            // Server told us this device's session is revoked. AuthContext picks it up via
            // the SESSION_REVOKED handler that already ran. No reconnect.
            if (event.code === CLOSE_CODE_SESSION_REVOKED)
            {
                this.userSessionId = null;
                return;
            }
            if (!this.explicitlyDisconnected)
            {
                this.scheduleReconnect();
            }
        };

        socket.onerror = (event) =>
        {
            console.warn('[Realtime] socket error', event);
        };
    }

    private teardownSocket(): void
    {
        this.stopHeartbeat();
        if (this.socket)
        {
            this.socket.onopen = null;
            this.socket.onmessage = null;
            this.socket.onclose = null;
            this.socket.onerror = null;
            try { this.socket.close(); } catch { /* noop */ }
            this.socket = null;
        }
    }

    private startHeartbeat(): void
    {
        this.stopHeartbeat();
        this.heartbeatTimer = setInterval(() =>
        {
            this.send({type: 'PING'});
        }, HEARTBEAT_INTERVAL_MS);
    }

    private stopHeartbeat(): void
    {
        if (this.heartbeatTimer)
        {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }

    private scheduleReconnect(): void
    {
        if (this.reconnectTimer || this.explicitlyDisconnected) return;
        const delay = Math.min(RECONNECT_BASE_MS * (2 ** this.reconnectAttempts), RECONNECT_CAP_MS);
        this.reconnectAttempts += 1;
        this.reconnectTimer = setTimeout(() =>
        {
            this.reconnectTimer = null;
            const token = sessionStorage.getItem('accessToken');
            const claims = decodeToken(token ?? '');
            if (isTokenExpired(claims))
            {
                this.userSessionId = null;
                return;
            }

            const sessionId = this.userSessionId ?? claims?.session_id ?? null;
            if (!token || !sessionId)
            {
                // Token disappeared (logout). Stop trying.
                this.userSessionId = null;
                return;
            }
            this.userSessionId = sessionId;
            this.openSocket(token, sessionId);
        }, delay);
    }

    private dispatch(msg: RealtimeMessage): void
    {
        // Built-in auth-event handling: any device-level revocation triggers the existing
        // auth-session-expired event. AuthContext already wires this to a redirect.
        if (msg.type === 'SESSION_REVOKED' || msg.type === 'PASSWORD_CHANGED')
        {
            const reason = msg.reason ?? msg.type;
            window.dispatchEvent(new CustomEvent('auth-session-expired', {detail: {reason}}));
        }

        // Typed handlers.
        const exact = this.typedHandlers.get(msg.type);
        exact?.forEach((h) => safeCall(h, msg));
        const wildcard = this.typedHandlers.get('*');
        wildcard?.forEach((h) => safeCall(h, msg));

        // Legacy notification handlers.
        if (msg.type === 'NOTIFICATION' && msg.notification)
        {
            this.legacyNotificationHandlers.forEach((h) =>
            {
                try { h(msg.notification!); }
                catch (err) { console.error('Realtime: legacy handler threw', err); }
            });
        }
    }
}

function safeCall(handler: TypedHandler, msg: RealtimeMessage): void
{
    try { handler(msg); }
    catch (err) { console.error('Realtime: typed handler threw', err); }
}

function decodeToken(token: string): AccessTokenClaims | null
{
    try { return jwtDecode<AccessTokenClaims>(token); }
    catch { return null; }
}

function isTokenExpired(claims: AccessTokenClaims | null): boolean
{
    if (!claims?.exp)
    {
        return true;
    }

    const nowInSeconds = Date.now() / 1000;
    return claims.exp <= nowInSeconds;
}

export const realtimeService = new RealtimeService();
// Back-compat alias for files still importing `notificationService`.
export const notificationService = realtimeService;
