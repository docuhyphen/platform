/** @vitest-environment jsdom */
import {afterEach, describe, expect, it, vi} from 'vitest';
import {act, renderHook} from '@testing-library/react';
import {saveExchangeFieldValues} from '../../../../services/fieldsService';
import {useFieldValuesSave} from './useFieldValuesSave';

vi.mock('../../../../services/fieldsService', () => ({
    saveExchangeFieldValues: vi.fn(),
}));

const entries = [{fieldContractId: 'contract-1', value: 'recorded note'}];

/** A save answered with the version it produced, as the service reports one. */
const savedAt = (etag: string) => ({outcome: 'SAVED' as const, assignment: {etag} as never});

afterEach(() => vi.mocked(saveExchangeFieldValues).mockReset());

describe('useFieldValuesSave', () =>
{
    it('states the version the values it is showing were served in', async () =>
    {
        vi.mocked(saveExchangeFieldValues).mockResolvedValue(savedAt('"set-1:8"'));
        const onValuesChanged = vi.fn();
        const {result} = renderHook(
            () => useFieldValuesSave('exchange-1', '"set-1:7"', onValuesChanged),
        );

        await act(async () => { await result.current.save(entries); });

        expect(saveExchangeFieldValues)
            .toHaveBeenCalledWith('exchange-1', {values: entries}, '"set-1:7"');
        expect(result.current.error).toBeNull();
        expect(result.current.saving).toBe(false);
        expect(onValuesChanged).toHaveBeenCalledTimes(1);
    });

    it('states the version its own last save produced, without waiting to be served it again', async () =>
    {
        vi.mocked(saveExchangeFieldValues).mockResolvedValue(savedAt('"set-1:8"'));
        const {result} = renderHook(
            () => useFieldValuesSave('exchange-1', '"set-1:7"', vi.fn()),
        );

        await act(async () => { await result.current.save(entries); });
        await act(async () => { await result.current.save(entries); });

        expect(saveExchangeFieldValues)
            .toHaveBeenNthCalledWith(2, 'exchange-1', {values: entries}, '"set-1:8"');
    });

    it('states the version it is newly served when the stored values are reloaded', async () =>
    {
        vi.mocked(saveExchangeFieldValues).mockResolvedValue(savedAt('"set-1:9"'));
        const {result, rerender} = renderHook(
            ({served}) => useFieldValuesSave('exchange-1', served, vi.fn()),
            {initialProps: {served: '"set-1:7"'}},
        );

        rerender({served: '"set-1:8"'});
        await act(async () => { await result.current.save(entries); });

        expect(saveExchangeFieldValues)
            .toHaveBeenCalledWith('exchange-1', {values: entries}, '"set-1:8"');
    });

    it('does not retry a save whose version moved on, and says it was not applied', async () =>
    {
        vi.mocked(saveExchangeFieldValues).mockResolvedValue({outcome: 'STALE'});
        const onValuesChanged = vi.fn();
        const {result} = renderHook(
            () => useFieldValuesSave('exchange-1', '"set-1:7"', onValuesChanged),
        );

        await act(async () => { await result.current.save(entries); });

        expect(saveExchangeFieldValues).toHaveBeenCalledTimes(1);
        expect(onValuesChanged).toHaveBeenCalledTimes(1);
        expect(result.current.error).toContain('not applied');
        expect(result.current.saving).toBe(false);
    });

    it('surfaces the message the server stated for a refusal it cannot recover from', async () =>
    {
        vi.mocked(saveExchangeFieldValues).mockRejectedValue({errorMessage: 'Recorded note is required'});
        const onValuesChanged = vi.fn();
        const {result} = renderHook(
            () => useFieldValuesSave('exchange-1', '"set-1:7"', onValuesChanged),
        );

        await act(async () => { await result.current.save(entries); });

        expect(result.current.error).toBe('Recorded note is required');
        expect(onValuesChanged).not.toHaveBeenCalled();
    });

    it('clears the previous refusal when the responder saves again', async () =>
    {
        vi.mocked(saveExchangeFieldValues).mockResolvedValueOnce({outcome: 'STALE'});
        vi.mocked(saveExchangeFieldValues).mockResolvedValueOnce(savedAt('"set-1:8"'));
        const {result} = renderHook(
            () => useFieldValuesSave('exchange-1', '"set-1:7"', vi.fn()),
        );

        await act(async () => { await result.current.save(entries); });
        await act(async () => { await result.current.save(entries); });

        expect(result.current.error).toBeNull();
    });
});
