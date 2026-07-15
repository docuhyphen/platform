/** @vitest-environment jsdom */
import React from 'react';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {NotificationProvider, useNotifications} from './NotificationContext';

const mocks = vi.hoisted(() => ({
    notificationHandler: null as ((notification: unknown) => void) | null,
    probeHandler: null as ((message: {message?: string; serverTime?: number}) => void) | null,
    fetchNotifications: vi.fn(),
}));

vi.mock('./AuthContext.tsx', () => ({
    useAuth: () => ({appUser: {id: 'user-1'}}),
}));

vi.mock('../services/notificationApi', () => ({
    fetchNotifications: (limit: number, cursor?: {timestamp: number; id: string}) =>
        mocks.fetchNotifications(limit, cursor),
}));

vi.mock('../services/BrowserNotificationService.ts', () => ({
    showBrowserNotification: vi.fn(),
}));

vi.mock('../services/NotificationService.tsx', () => ({
    notificationService: {
        connect: vi.fn(),
        disconnect: vi.fn(),
        addMessageHandler: (handler: (notification: unknown) => void) =>
        {
            mocks.notificationHandler = handler;
            return vi.fn();
        },
        on: (type: string, handler: (message: {message?: string; serverTime?: number}) => void) =>
        {
            if (type === 'REALTIME_PROBE') mocks.probeHandler = handler;
            return vi.fn();
        },
    },
}));

vi.mock('../app/components/global-realtime-toast/GlobalRealtimeToast', () => ({
    default: ({event}: {event: {message: string} | null}) => (
        <div id="test-global-realtime-toast">{event?.message}</div>
    ),
}));

const NotificationConsumer: React.FC = () =>
{
    const {notifications, unreadCount, hasMoreNotifications, loadMoreNotifications} = useNotifications();
    return (
        <div id="test-notification-consumer">
            <span id="test-unread-count">{unreadCount}</span>
            <span id="test-has-more">{String(hasMoreNotifications)}</span>
            <button
                id="test-load-more"
                type="button"
                onClick={() => void loadMoreNotifications()}
            >
                Load more
            </button>
            {notifications.map((notification) => (
                <span
                    id={`test-notification-${notification.id}`}
                    key={notification.id}
                >
                    {notification.message}
                </span>
            ))}
        </div>
    );
};

afterEach(() =>
{
    cleanup();
    mocks.fetchNotifications.mockReset();
    mocks.notificationHandler = null;
    mocks.probeHandler = null;
});

describe('NotificationProvider', () =>
{
    it('hydrates persisted notifications and adds probe notifications with a global toast', async () =>
    {
        mocks.fetchNotifications.mockResolvedValue({
            notifications: [{
                id: 'persisted-1',
                type: 'workflow.step_assigned',
                message: 'Approval required',
                timestamp: '2026-07-15T12:00:00.000Z',
                isRead: false,
                data: {},
            }],
            hasMore: false,
            unreadCount: 1,
        });

        render(
            <NotificationProvider>
                <NotificationConsumer/>
            </NotificationProvider>,
        );

        await waitFor(() => expect(screen.getByText('Approval required')).toBeTruthy());
        expect(document.getElementById('test-unread-count')?.textContent).toBe('1');

        mocks.probeHandler?.({message: 'Probe received', serverTime: 1234});

        await waitFor(() =>
            expect(document.getElementById('test-notification-realtime-probe-1234')).toBeTruthy(),
        );
        expect(document.getElementById('test-global-realtime-toast')?.textContent).toBe('Probe received');
        expect(document.getElementById('test-unread-count')?.textContent).toBe('2');
    });

    it('loads and deduplicates the next notification page', async () =>
    {
        mocks.fetchNotifications
            .mockResolvedValueOnce({
                notifications: [{
                    id: 'notification-2',
                    type: 'exchange.accepted',
                    message: 'Newest notification',
                    timestamp: '2026-07-15T12:00:00.000Z',
                    isRead: true,
                    data: {},
                }],
                nextCursor: {timestamp: 1000, id: 'cursor-1'},
                hasMore: true,
                unreadCount: 0,
            })
            .mockResolvedValueOnce({
                notifications: [
                    {
                        id: 'notification-2',
                        type: 'exchange.accepted',
                        message: 'Newest notification',
                        timestamp: '2026-07-15T12:00:00.000Z',
                        isRead: true,
                        data: {},
                    },
                    {
                        id: 'notification-1',
                        type: 'exchange.initiated',
                        message: 'Older notification',
                        timestamp: '2026-07-14T12:00:00.000Z',
                        isRead: true,
                        data: {},
                    },
                ],
                hasMore: false,
                unreadCount: 0,
            });

        render(
            <NotificationProvider>
                <NotificationConsumer/>
            </NotificationProvider>,
        );

        await waitFor(() => expect(screen.getByText('Newest notification')).toBeTruthy());
        expect(document.getElementById('test-has-more')?.textContent).toBe('true');

        fireEvent.click(screen.getByRole('button', {name: 'Load more'}));

        await waitFor(() => expect(screen.getByText('Older notification')).toBeTruthy());
        expect(screen.getAllByText('Newest notification')).toHaveLength(1);
        expect(document.getElementById('test-has-more')?.textContent).toBe('false');
        expect(mocks.fetchNotifications).toHaveBeenLastCalledWith(20, {timestamp: 1000, id: 'cursor-1'});
    });
});
