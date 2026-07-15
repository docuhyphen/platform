/** @vitest-environment jsdom */
import React from 'react';
import {fireEvent, render, screen} from '@testing-library/react';
import {MemoryRouter, useNavigate} from 'react-router-dom';
import {describe, expect, it} from 'vitest';
import {useExchangeRouteState} from './useExchangeRouteState';

const RouteStateHarness: React.FC = () =>
{
    const routeState = useExchangeRouteState();
    const navigate = useNavigate();

    return (
        <div id="exchange-route-state-harness">
            <span id="exchange-route-state-tab">{routeState.listTab ?? 'none'}</span>
            <span id="exchange-route-state-exchange">{routeState.exchangeId ?? 'none'}</span>
            <span id="exchange-route-state-document">{routeState.documentId ?? 'none'}</span>
            <button
                id="exchange-route-state-navigate"
                type="button"
                onClick={() => navigate('/exchanges?s=exchange-2&d=document-2')}
            >
                Open notification
            </button>
        </div>
    );
};

describe('useExchangeRouteState', () =>
{
    it('updates Exchange and document selection when navigation changes only the query string', () =>
    {
        render(
            <MemoryRouter initialEntries={['/exchanges?tab=archive&s=exchange-1']}>
                <RouteStateHarness/>
            </MemoryRouter>,
        );

        expect(screen.getByText('archive')).toBeTruthy();
        expect(screen.getByText('exchange-1')).toBeTruthy();

        fireEvent.click(screen.getByRole('button', {name: 'Open notification'}));

        expect(screen.getByText('exchange-2')).toBeTruthy();
        expect(screen.getByText('document-2')).toBeTruthy();
    });
});
