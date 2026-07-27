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
