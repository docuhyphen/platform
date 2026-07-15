import {describe, expect, it} from 'vitest';
import {NotificationDto} from '../app/models/models';
import {getNotificationTarget} from './notificationNavigation';

const notification = (overrides: Partial<NotificationDto>): NotificationDto => ({
    id: 'notification-1',
    type: 'exchange.initiated',
    message: 'An Exchange is ready',
    timestamp: '2026-07-15T12:00:00.000Z',
    isRead: false,
    data: {},
    ...overrides,
});

describe('getNotificationTarget', () =>
{
    it('opens an Exchange and its document when both identifiers are present', () =>
    {
        expect(getNotificationTarget(notification({
            exchangeId: 'exchange-1',
            documentId: 'document-1',
        }))).toBe('/exchanges?s=exchange-1&d=document-1');
    });

    it('uses Exchange subject metadata from workflow notifications', () =>
    {
        expect(getNotificationTarget(notification({
            exchangeId: undefined,
            data: {subjectType: 'EXCHANGE', subjectId: 'exchange-2'},
        }))).toBe('/exchanges?s=exchange-2');
    });

    it('does not create a target for notifications without entity metadata', () =>
    {
        expect(getNotificationTarget(notification({type: 'REALTIME_PROBE'}))).toBeNull();
    });

    it('ignores null identifiers returned by the notification API', () =>
    {
        expect(getNotificationTarget(notification({
            exchangeId: null,
            documentId: null,
            data: {subjectType: 'EXCHANGE', subjectId: 'exchange-3'},
        }))).toBe('/exchanges?s=exchange-3');
    });
});
