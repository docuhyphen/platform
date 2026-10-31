/** @vitest-environment jsdom */
import {act, cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {AppUserDetailedDto, ExchangeBasicDto, ExchangeDetailedDto, ExchangeStatus} from '../models/models.tsx';
import Exchanges from './Exchanges.tsx';

const mocks = vi.hoisted(() => ({
    fetchDetails: vi.fn(),
    searchExchanges: vi.fn(),
    appUser: null as AppUserDetailedDto | null,
    sessionReady: true,
}));

vi.mock('../../context/AuthContext.tsx', () => ({
    useAuth: () => ({
        appUser: mocks.appUser,
        currentSession: mocks.sessionReady ? {} : null,
        hasCapability: () => false
    }),
}));
vi.mock('../../context/useToken.tsx', () => ({default: () => 'access-token'}));
vi.mock('../../utils/useMediaQuery.ts', () => ({useIsMobile: () => false}));
vi.mock('../../hooks/subscription/usePlanFeature.ts', () => ({
    usePlanFeature: () => ({isDiscoverable: false}),
}));
vi.mock('../../services/exchangeApi.ts', () => ({
    fetchSignedInUserAppUserExchange: (...args: unknown[]) => mocks.fetchDetails(...args),
    searchExchanges: (...args: unknown[]) => mocks.searchExchanges(...args),
}));
vi.mock('./exchangeWorkspaceAvailability.ts', () => ({hasExchangeWorkspaceContent: async () => true}));
vi.mock('../../services/NotificationService', () => ({
    realtimeService: {
        on: () => () => undefined,
        subscribeToExchange: () => undefined,
        unsubscribeFromExchange: () => undefined,
    },
}));
vi.mock('../information-requests/exchange-tab/useExchangeInformationRequests.ts', () => ({
    useExchangeInformationRequests: () => ({loading: false, visible: false}),
}));
vi.mock('./components/exchange-details-header/ExchangeDetailsHeader.tsx', () => ({
    default: ({exchangeDetails}: { exchangeDetails: ExchangeDetailedDto }) => (
        <h2 id="exchange-details-test-header">{exchangeDetails.name}</h2>
    ),
}));
vi.mock('./components/exchange-document-list/ExchangeDocumentsList.tsx', () => ({default: () => null}));
vi.mock('./components/exchange-document-preview/ExchangeDocumentPreviewer.tsx', () => ({default: () => null}));
vi.mock('./components/exchange-document-sidebar/ExchangeDocumentSidebar.tsx', () => ({default: () => null}));
vi.mock('./components/exchange-dialog-group/ExchangeDialogsGroup.tsx', () => ({default: () => null}));
vi.mock('./components/exchange-tabs-header/ExchangeTabsHeader.tsx', () => ({default: () => null}));
vi.mock('./components/exchange-acceptance-dialog/ExchangeAcceptanceDialog.tsx', () => ({default: () => null}));
vi.mock('./components/exchange-audit-tab/ExchangeAuditTab.tsx', () => ({default: () => null}));
vi.mock('./components/exchange-workflow-tab/ExchangeWorkflowTab.tsx', () => ({default: () => null}));
vi.mock('./components/exchange-fields-tab/ExchangeFieldsTab.tsx', () => ({default: () => null}));
vi.mock('../information-requests/exchange-tab/exchange-information-requests-tab/ExchangeInformationRequestsTab.tsx', () => ({default: () => null}));

const firstExchange: ExchangeDetailedDto = {
    id: 'exchange-1',
    name: 'First document collection',
    createdDate: '2026-08-01T12:00:00Z',
    lastActivity: '2026-08-01T12:00:00Z',
    endDate: '',
    status: ExchangeStatus.ACCEPTED_STARTED,
    documents: [],
};
const secondExchange: ExchangeDetailedDto = {...firstExchange, id: 'exchange-2', name: 'Second document collection'};
const page = (content: ExchangeBasicDto[]) => ({
    content,
    totalElements: content.length,
    totalPages: content.length > 0 ? 1 : 0,
    currentPage: 0,
    pageSize: 30,
});
const deferred = <T, >() => {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>(complete => {
        resolve = complete;
    });
    return {promise, resolve};
};
const renderExchanges = (route = '/exchanges') => render(
    <MemoryRouter initialEntries={[route]}>
        <Exchanges/>
    </MemoryRouter>,
);

describe('Exchanges initial details loading', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        window.history.replaceState(null, '', '/exchanges');
        window.localStorage.clear();
        Object.defineProperty(globalThis, 'NodeFilter', {configurable: true, value: window.NodeFilter});
        mocks.appUser = {id: 'user-1', settings: {autoPreviewDocuments: false}} as AppUserDetailedDto;
        mocks.sessionReady = true;
        mocks.searchExchanges.mockImplementation(async (_query: unknown, status: string) =>
            page(status === 'ACCEPTED_STARTED'
                ? [firstExchange, secondExchange].map(({id, name, createdDate, lastActivity, status}) =>
                    ({id, name, createdDate, lastActivity, status}))
                : []));
        mocks.fetchDetails.mockImplementation(async (id: string) => id === firstExchange.id ? firstExchange : secondExchange);
    });

    afterEach(() => {
        cleanup();
        vi.restoreAllMocks();
    });

    it('shows the automatically selected Exchange after signing in without another selection', async () => {
        const details = deferred<ExchangeDetailedDto>();
        mocks.fetchDetails.mockReturnValue(details.promise);
        const {rerender} = renderExchanges();
        mocks.appUser = {...mocks.appUser!, settings: undefined};
        rerender(<MemoryRouter initialEntries={['/exchanges']}><Exchanges/></MemoryRouter>);
        await screen.findByText(firstExchange.name!);
        await screen.findByRole('region', {name: 'Loading Exchange details'});

        await act(async () => details.resolve(firstExchange));
        mocks.appUser = {...mocks.appUser!, settings: {autoPreviewDocuments: false}} as AppUserDetailedDto;
        rerender(<MemoryRouter initialEntries={['/exchanges']}><Exchanges/></MemoryRouter>);

        expect(await screen.findByRole('heading', {name: firstExchange.name})).toBeTruthy();
        await waitFor(() => expect(screen.queryByRole('region', {name: 'Loading Exchange details'})).toBeNull());
    });

    it('ignores the initial response when a newer Exchange selection has already loaded', async () => {
        const initialDetails = deferred<ExchangeDetailedDto>();
        mocks.fetchDetails.mockImplementation((id: string) =>
            id === firstExchange.id ? initialDetails.promise : Promise.resolve(secondExchange));
        renderExchanges();
        await screen.findByText(firstExchange.name!);
        await screen.findByRole('region', {name: 'Loading Exchange details'});

        fireEvent.click(screen.getByText(secondExchange.name!));
        expect(await screen.findByRole('heading', {name: secondExchange.name})).toBeTruthy();
        await act(async () => initialDetails.resolve(firstExchange));

        await waitFor(() => expect(screen.queryByRole('region', {name: 'Loading Exchange details'})).toBeNull());
        expect(screen.getByRole('heading', {name: secondExchange.name})).toBeTruthy();
    });

    it('preserves an initial route selection when the first list response arrives', async () => {
        const initialDetails = deferred<ExchangeDetailedDto>();
        mocks.fetchDetails.mockImplementation((id: string) =>
            id === secondExchange.id ? initialDetails.promise : Promise.resolve(firstExchange));
        renderExchanges(`/exchanges?s=${secondExchange.id}`);
        await screen.findByText(firstExchange.name!);
        await screen.findByRole('region', {name: 'Loading Exchange details'});
        await act(async () => initialDetails.resolve(secondExchange));

        expect(await screen.findByRole('heading', {name: secondExchange.name})).toBeTruthy();
        expect(mocks.fetchDetails).not.toHaveBeenCalledWith(firstExchange.id);
        await waitFor(() => expect(screen.queryByRole('region', {name: 'Loading Exchange details'})).toBeNull());
    });

    it('waits for sign-in session initialization before loading Exchange details', async () => {
        mocks.sessionReady = false;
        const {rerender} = renderExchanges();
        await act(async () => undefined);
        expect(mocks.fetchDetails).not.toHaveBeenCalled();
        expect(mocks.searchExchanges).not.toHaveBeenCalled();

        mocks.sessionReady = true;
        rerender(<MemoryRouter initialEntries={['/exchanges']}><Exchanges/></MemoryRouter>);

        expect(await screen.findByRole('heading', {name: firstExchange.name})).toBeTruthy();
        expect(mocks.fetchDetails).toHaveBeenCalledTimes(1);
    });

    it('replaces a failed initial load with a retry that opens the same Exchange', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        mocks.fetchDetails.mockRejectedValueOnce(new Error('Detail request failed'));
        renderExchanges();

        expect(await screen.findByRole('alert')).toBeTruthy();
        expect(screen.queryByRole('region', {name: 'Loading Exchange details'})).toBeNull();
        fireEvent.click(screen.getByRole('button', {name: 'Retry'}));

        expect(await screen.findByRole('heading', {name: firstExchange.name})).toBeTruthy();
        expect(screen.queryByRole('alert')).toBeNull();
        expect(mocks.fetchDetails).toHaveBeenCalledTimes(2);
    });
});
