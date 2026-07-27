// @vitest-environment jsdom

import {act, renderHook} from '@testing-library/react';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {useRegisteredPersonAccess} from './useRegisteredPersonAccess.ts';

const mocks = vi.hoisted(() => ({
    grantAccess: vi.fn(),
}));

vi.mock('../../../../../services/exchangeApi.ts', () => ({
    grantExchangeAccess: (...args: unknown[]) => mocks.grantAccess(...args),
}));

vi.mock('../../../../../services/personalContactsApi.ts', () => ({
    searchContacts: vi.fn().mockResolvedValue([]),
}));

describe('useRegisteredPersonAccess', () =>
{
    beforeEach(() =>
    {
        vi.clearAllMocks();
    });

    it('shows a structured API error message instead of stringifying the response object', async () =>
    {
        mocks.grantAccess.mockRejectedValue({
            errorMessage: 'The active organization does not own this Exchange',
        });
        const onAdded = vi.fn();
        const {result} = renderHook(() => useRegisteredPersonAccess('exchange-1', onAdded));

        act(() =>
        {
            result.current.setEmail('person@example.com');
        });
        await act(async () =>
        {
            await result.current.add();
        });

        expect(result.current.error).toBe('The active organization does not own this Exchange');
        expect(onAdded).not.toHaveBeenCalled();
    });
});
