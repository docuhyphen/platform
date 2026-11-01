import {jwtDecode} from "jwt-decode";
import {NotificationDto} from '../app/models/models';
import {getApiBaseUrl} from './apiBaseUrl.ts';
import {issueRealtimeTicket} from './realtimeApi.ts';

export type RealtimeMessageType =
    | 'PING'
    | 'PONG'
    | 'SUBSCRIBE_EXCHANGE'
    | 'UNSUBSCRIBE_EXCHANGE'
    | 'SESSION_REVOKED'
    | 'SESSION_CREATED'
    | 'SESSION_REMOVED'
    | 'PASSWORD_CHANGED'
    | 'SIGNED_OUT_OTHER_DEVICE'
    | 'NOTIFICATION'
    | 'PRESENCE_UPDATE'
    | 'SHARING_VIEWERS'
    | 'EXCHANGE_DOCUMENT_ADDED'
    | 'EXCHANGE_DOCUMENT_REMOVED'
    | 'EXCHANGE_DOCUMENT_UPDATED'
    | 'DOCUMENT_COMMENT_ADDED'
    | 'EXCHANGE_STATUS_CHANGED'
    | 'EXCHANGE_LIST_CHANGED'
    | 'REALTIME_PROBE'
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
    exchangeId?: string;
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
    commentId?: string;
    status?: string;
}

type TypedHandler = (msg: RealtimeMessage) => void;
type LegacyNotificationHandler = (notification: NotificationDto) => void;

interface AccessTokenClaims
{
    sub?: string;
    exchange_id?: string;
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
    private revocationHandled = false;
    private connectionAttempt = 0;

    private typedHandlers = new Map<RealtimeMessageType | '*', Set<TypedHandler>>();
    private legacyNotificationHandlers = new Set<LegacyNotificationHandler>();

    connect(appUserId?: string): void
    {
        void appUserId;
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

        const sessionId = claims?.exchange_id;
        if (!sessionId)
        {
            console.warn('[Realtime] access token missing exchange_id claim', claims);
            return;
        }

        this.explicitlyDisconnected = false;
        this.revocationHandled = false;

        if (this.socket && this.userSessionId === sessionId && this.socket.readyState <= WebSocket.OPEN)
        {
            console.info('[Realtime] already connected for this session, skipping');
            return;
        }

        this.teardownSocket();
        this.userSessionId = sessionId;
        const attempt = ++this.connectionAttempt;
        void this.openSocketWithTicket(sessionId, attempt);
    }

    disconnect(): void
    {
        this.explicitlyDisconnected = true;
        this.connectionAttempt += 1;
        if (this.reconnectTimer)
        {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
        this.teardownSocket();
        this.userSessionId = null;
        this.reconnectAttempts = 0;
    }

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

    addMessageHandler(handler: LegacyNotificationHandler): () => void
    {
        this.legacyNotificationHandlers.add(handler);
        return () => { this.legacyNotificationHandlers.delete(handler); };
    }

    send(message: RealtimeMessage): void
    {
        if (this.socket && this.socket.readyState === WebSocket.OPEN)
        {
            this.socket.send(JSON.stringify(message));
        }
    }

    subscribeToExchange(exchangeId: string): void
    {
        this.send({type: 'SUBSCRIBE_EXCHANGE', exchangeId});
    }

    unsubscribeFromExchange(exchangeId: string): void
    {
        this.send({type: 'UNSUBSCRIBE_EXCHANGE', exchangeId});
    }

    private async openSocketWithTicket(userSessionId: string, attempt: number): Promise<void>
    {
        try
        {
            const {ticket} = await issueRealtimeTicket();
            if (this.explicitlyDisconnected || attempt !== this.connectionAttempt || this.userSessionId !== userSessionId)
            {
                return;
            }
            this.openSocket(ticket, userSessionId);
        }
        catch (error: unknown)
        {
            console.warn('[Realtime] failed to issue connection ticket', error);
            if (!this.explicitlyDisconnected && attempt === this.connectionAttempt)
            {
                this.scheduleReconnect();
            }
        }
    }

    private openSocket(ticket: string, userSessionId: string): void
    {
        const apiBase = getApiBaseUrl();
        const wsBase = apiBase
            ? apiBase.replace(/^https:\/\//, 'wss://').replace(/^http:\/\//, 'ws://')
            : 'ws://localhost:8080';
        const url = `${wsBase}/realtime/${encodeURIComponent(userSessionId)}?ticket=${encodeURIComponent(ticket)}`;

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
            this.reconnectAttempts = 0;
            this.startHeartbeat();
        };

        socket.onmessage = (event) =>
        {
            try
            {
                const msg = JSON.parse(event.data) as RealtimeMessage;
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
            if (event.code === CLOSE_CODE_AUTH_FAILED)
            {
                console.warn('[Realtime] auth failed, not reconnecting until token refresh');
                return;
            }
            if (event.code === CLOSE_CODE_SESSION_REVOKED)
            {
                if (!this.explicitlyDisconnected && !this.revocationHandled)
                {
                    const reasonPrefix = 'session revoked: ';
                    const reason = event.reason.startsWith(reasonPrefix)
                        ? event.reason.slice(reasonPrefix.length)
                        : undefined;
                    window.dispatchEvent(new CustomEvent('auth-session-expired', {
                        detail: reason ? {reason} : undefined,
                    }));
                }
                this.userSessionId = null;
                this.revocationHandled = false;
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

            const sessionId = this.userSessionId ?? claims?.exchange_id ?? null;
            if (!token || !sessionId)
            {
                this.userSessionId = null;
                return;
            }
            this.userSessionId = sessionId;
            const attempt = ++this.connectionAttempt;
            void this.openSocketWithTicket(sessionId, attempt);
        }, delay);
    }

    private dispatch(msg: RealtimeMessage): void
    {
        if (msg.type === 'SESSION_REVOKED' || msg.type === 'PASSWORD_CHANGED')
        {
            this.revocationHandled = true;
            const reason = msg.reason ?? msg.type;
            window.dispatchEvent(new CustomEvent('auth-session-expired', {detail: {reason}}));
        }

        const exact = this.typedHandlers.get(msg.type);
        exact?.forEach((h) => safeCall(h, msg));
        const wildcard = this.typedHandlers.get('*');
        wildcard?.forEach((h) => safeCall(h, msg));

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
export const notificationService = realtimeService;
