/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest';
import {ExchangeDetailedDto, ExchangeStatus} from '../../../models/models.tsx';
import ExchangeAcceptanceDialog from './ExchangeAcceptanceDialog.tsx';

const mocks = vi.hoisted(() => ({
    updateExchange: vi.fn(),
    fetchExchange: vi.fn(),
    publishExchangeUpdate: vi.fn(),
}));

vi.mock('../../../../services/exchangeApi.ts', () => ({
    updateExchange: (...args: unknown[]) => mocks.updateExchange(...args),
    fetchSignedInUserAppUserExchange: (...args: unknown[]) => mocks.fetchExchange(...args),
}));

vi.mock('../../../observable/exchangeObservables.ts', () => ({
    publishExchangeUpdate: (...args: unknown[]) => mocks.publishExchangeUpdate(...args),
}));

describe('ExchangeAcceptanceDialog', () =>
{
    beforeAll(() =>
    {
        vi.stubGlobal('ResizeObserver', class
        {
            observe() {}
            unobserve() {}
            disconnect() {}
        });
    });
    beforeEach(() =>
    {
        mocks.updateExchange.mockReset();
        mocks.fetchExchange.mockReset();
        mocks.publishExchangeUpdate.mockReset();
    });
    afterEach(cleanup);

    it('renders stable IDs for the acceptance summary and decision controls', () =>
    {
        const exchange = {
            id: 'exchange-ids',
            name: 'ID Coverage Exchange',
            initialShareMessage: 'Please review the requested and shared documents.',
            status: ExchangeStatus.INITIATED,
            documents: [
                {
                    id: 'requested-document',
                    title: 'Requested document',
                },
                {
                    id: 'shared-document',
                    title: 'Shared document',
                    uploadDate: '2026-07-27T10:00:00Z',
                },
            ],
            initiator: {
                id: 'initiator-ids',
                email: 'initiator@example.test',
            },
        } as ExchangeDetailedDto;

        render(
            <ExchangeAcceptanceDialog
                exchange={exchange}
                isSingleExchange={true}
                canDecideLater={false}
                activeCount={0}
                archiveCount={0}
                onAccepted={vi.fn()}
                onRejected={vi.fn()}
                onDismiss={vi.fn()}
                onOpenActive={vi.fn()}
                onOpenArchive={vi.fn()}
            />,
        );

        [
            'exchange-acceptance-overlay',
            'exchange-acceptance-title-divider',
            'exchange-acceptance-requester-persona',
            'exchange-acceptance-exchange-name',
            'exchange-acceptance-message-text',
            'exchange-acceptance-requested-document-requested-document',
            'exchange-acceptance-shared-document-shared-document',
            'exchange-acceptance-actions-divider',
            'acceptance-accept-btn',
            'acceptance-decline-btn',
        ].forEach(id => expect(document.getElementById(id)).not.toBeNull());
    });

    it('does not refetch an Exchange after the recipient rejects it', async () =>
    {
        const exchange = {
            id: 'exchange-1',
            name: 'Rejected Exchange',
            status: ExchangeStatus.INITIATED,
            documents: [],
            initiator: {
                id: 'initiator-1',
                email: 'initiator@example.test',
            },
        } as ExchangeDetailedDto;
        const onRejected = vi.fn();
        mocks.updateExchange.mockResolvedValue(undefined);

        render(
            <ExchangeAcceptanceDialog
                exchange={exchange}
                isSingleExchange={true}
                canDecideLater={false}
                activeCount={0}
                archiveCount={0}
                onAccepted={vi.fn()}
                onRejected={onRejected}
                onDismiss={vi.fn()}
                onOpenActive={vi.fn()}
                onOpenArchive={vi.fn()}
            />,
        );

        fireEvent.click(screen.getByRole('button', {name: 'Decline'}));
        fireEvent.click(screen.getByRole('button', {name: 'Confirm Decline'}));

        await waitFor(() => expect(mocks.updateExchange).toHaveBeenCalledWith(
            'exchange-1',
            {status: ExchangeStatus.REJECTED, rejectionReason: ''},
        ));
        expect(mocks.fetchExchange).not.toHaveBeenCalled();
        expect(mocks.publishExchangeUpdate).not.toHaveBeenCalled();
        expect(onRejected).toHaveBeenCalledWith('exchange-1');
    });
});
