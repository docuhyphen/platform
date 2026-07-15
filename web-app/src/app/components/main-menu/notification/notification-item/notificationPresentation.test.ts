import {describe, expect, it} from 'vitest';
import {NotificationDto} from '../../../../models/models.tsx';
import {getNotificationMessage} from './notificationPresentation.ts';

const notification = (overrides: Partial<NotificationDto>): NotificationDto => ({
    id: 'notification-1',
    type: 'workflow.step_assigned',
    message: 'Subject: EXCHANGE 95be5a4a-f926-4e6e-9933-00ecb29fc026',
    timestamp: '2026-07-15T12:00:00.000Z',
    isRead: false,
    data: {},
    ...overrides,
});

describe('getNotificationMessage', () =>
{
    it('replaces a stored workflow subject with named Exchange context', () =>
    {
        const message = getNotificationMessage(notification({
            data: {
                exchangeName: 'Annual records',
                initiatorName: 'Amina Patel',
            },
        }));

        expect(message).toBe('Amina Patel requested your approval for Exchange "Annual records".');
    });

    it('never displays a legacy technical subject when names are unavailable', () =>
    {
        const message = getNotificationMessage(notification({data: {}}));

        expect(message).toBe('Review and decide on the pending approval.');
        expect(message).not.toContain('EXCHANGE');
        expect(message).not.toContain('95be5a4a-f926-4e6e-9933-00ecb29fc026');
    });

    it('identifies the document and Exchange for a comment notification', () =>
    {
        const message = getNotificationMessage(notification({
            type: 'document.commented',
            message: 'Jane Doe made a comment to a document',
            data: {
                commenterName: 'Jane Doe',
                documentName: 'Signed agreement.pdf',
                exchangeName: 'Supplier onboarding',
            },
        }));

        expect(message).toBe(
            'Jane Doe commented on "Signed agreement.pdf" in Exchange "Supplier onboarding".',
        );
    });

    it('improves legacy comment notifications without exposing vague copy', () =>
    {
        const message = getNotificationMessage(notification({
            type: 'document.commented',
            message: 'Jane Doe made a comment to a document',
            data: {},
        }));

        expect(message).toBe(
            'Jane Doe added a document comment. Open it to view the document and Exchange.',
        );
    });
});
