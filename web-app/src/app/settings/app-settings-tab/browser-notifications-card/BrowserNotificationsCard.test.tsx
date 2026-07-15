/** @vitest-environment jsdom */
import React from 'react';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import BrowserNotificationsCard from './BrowserNotificationsCard';

afterEach(() =>
{
    cleanup();
    vi.unstubAllGlobals();
});

describe('BrowserNotificationsCard', () =>
{
    it('lets a user request browser permission again after it was denied', async () =>
    {
        const requestPermission = vi.fn().mockResolvedValue('granted');
        vi.stubGlobal('Notification', {
            permission: 'denied',
            requestPermission,
        });

        render(<BrowserNotificationsCard/>);

        fireEvent.click(screen.getByRole('button', {name: 'Request again'}));

        await waitFor(() => expect(requestPermission).toHaveBeenCalledOnce());
        await waitFor(() => expect(screen.getByText('Enabled')).toBeTruthy());
    });
});
