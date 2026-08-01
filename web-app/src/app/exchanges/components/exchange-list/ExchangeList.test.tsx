/** @vitest-environment jsdom */
import React from 'react';
import {act, render, screen, waitFor} from '@testing-library/react';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {ExchangeBasicDto, ExchangeDetailedDto, ExchangeStatus} from '../../../models/models.tsx';
import {publishExchangeUpdate} from '../../../observable/exchangeObservables.ts';
import {searchExchanges} from '../../../../services/exchangeApi.ts';
import ExchangeList from './ExchangeList.tsx';

vi.mock('../../../../services/exchangeApi.ts', () => ({
    searchExchanges: vi.fn(),
}));

vi.mock('../../../../utils/useMediaQuery.ts', () => ({
    useIsMobile: () => false,
}));

vi.mock('../../../../services/NotificationService', () => ({
    realtimeService: {
        on: vi.fn(() => () => undefined),
    },
}));

const activeExchange: ExchangeBasicDto = {
    id: 'exchange-1',
    createdDate: '2026-08-01T12:00:00Z',
    lastActivity: '2026-08-01T12:00:00Z',
    name: 'Loan Application Package',
    status: ExchangeStatus.ACCEPTED_STARTED,
};

const endedExchange: ExchangeDetailedDto = {
    ...activeExchange,
    endDate: '2026-08-01T13:00:00Z',
    status: ExchangeStatus.ENDED,
    documents: [],
};

const page = (content: ExchangeBasicDto[]) => ({
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    currentPage: 0,
    pageSize: 30,
});

describe('ExchangeList lifecycle transitions', () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
        Object.defineProperty(globalThis, 'NodeFilter', {
            configurable: true,
            value: window.NodeFilter,
        });
        vi.mocked(searchExchanges).mockImplementation(async (_query, status) =>
        {
            if (status === 'ACCEPTED_STARTED') return page([activeExchange]);
            if (status === 'ENDED,REJECTED,RESCINDED') return page([endedExchange]);
            return page([]);
        });
    });

    it('keeps an ended Exchange selected while moving it from Active to Archive', async () =>
    {
        const onSelectionChange = vi.fn();
        const {rerender, unmount} = render(
            <ExchangeList
                onSelectionChange={onSelectionChange}
                controlledSelectedId={activeExchange.id}
                controlledActiveTab="active"
            />,
        );

        await screen.findByText('Loan Application Package');
        onSelectionChange.mockClear();

        act(() => publishExchangeUpdate(endedExchange));

        rerender(
            <ExchangeList
                onSelectionChange={onSelectionChange}
                controlledSelectedId={endedExchange.id}
                controlledActiveTab="archive"
            />,
        );

        await screen.findByText('Ended');
        await waitFor(() =>
        {
            expect(screen.getByRole('tab', {name: 'Archive'}).getAttribute('aria-selected')).toBe('true');
        });
        expect(onSelectionChange.mock.calls.some(([exchangeId]) => exchangeId == null)).toBe(false);

        unmount();
        await act(async () => undefined);
    });
});
