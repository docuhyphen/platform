import {beforeEach, describe, expect, it, vi} from 'vitest';

const patch = vi.fn();
const put = vi.fn();

vi.mock('../apiClient.ts', () => ({
    default: {
        patch: (...args: unknown[]) => patch(...args),
        put: (...args: unknown[]) => put(...args),
    },
}));

// SchemaAssignmentDto shape (see web-app/src/app/models/models.tsx) as returned by
// ExchangeFieldsResource.patchValues, carrying the validator of the version the save produced.
const savedAssignment = {
    id: 'assignment-1',
    resourceType: 'EXCHANGE',
    resourceId: 'exchange-1',
    schemaVersionId: 'version-1',
    schemaDefinitionId: 'definition-1',
    schemaKey: 'process.intake',
    displayName: 'Process intake',
    versionNumber: 1,
    assignmentSource: 'MANUAL',
    assignedAt: '2024-03-01T00:00:00Z',
    fields: [],
    etag: '"set-1:8"',
};

/** A refusal shaped as axios reports one, carrying the body the server states it with. */
const refusal = (status: number, reasonCode?: string) => ({
    isAxiosError: true,
    message: 'Request failed with status code ' + status,
    response: {status, data: {errorMessage: 'refused', reasonCode}},
});

const request = {values: [{fieldContractId: 'contract-1', value: 'recorded note'}]};

beforeEach(() =>
{
    patch.mockReset();
    put.mockReset();
});

describe('saveExchangeFieldValues', () =>
{
    it('states the version it was served and answers with the version the save produced', async () =>
    {
        patch.mockResolvedValueOnce({data: savedAssignment});
        const {saveExchangeFieldValues} = await import('../fieldsService.ts');

        const result = await saveExchangeFieldValues('exchange-1', request, '"set-1:7"');

        expect(patch).toHaveBeenCalledWith(
            '/exchanges/exchange-1/fields',
            request,
            {headers: {'If-Match': '"set-1:7"'}},
        );
        expect(result).toEqual({outcome: 'SAVED', assignment: savedAssignment});
    });

    it('does not reach the superseded write surface', async () =>
    {
        patch.mockResolvedValueOnce({data: savedAssignment});
        const {saveExchangeFieldValues} = await import('../fieldsService.ts');

        await saveExchangeFieldValues('exchange-1', request, '"set-1:7"');

        expect(put).not.toHaveBeenCalled();
    });

    it('accepts whichever version is current when it was served no validator', async () =>
    {
        patch.mockResolvedValueOnce({data: savedAssignment});
        const {saveExchangeFieldValues} = await import('../fieldsService.ts');

        await saveExchangeFieldValues('exchange-1', request, undefined);

        expect(patch).toHaveBeenCalledWith(
            '/exchanges/exchange-1/fields',
            request,
            {headers: {'If-Match': '*'}},
        );
    });

    it('reports a version that has moved on as an answer rather than as a failure', async () =>
    {
        patch.mockRejectedValueOnce(refusal(412, 'FIELDS_PRECONDITION_STALE'));
        const {saveExchangeFieldValues} = await import('../fieldsService.ts');

        await expect(saveExchangeFieldValues('exchange-1', request, '"set-1:7"'))
            .resolves.toEqual({outcome: 'STALE'});
    });

    it('throws a refusal it cannot recover from as the server stated it', async () =>
    {
        patch.mockRejectedValueOnce(refusal(400));
        const {saveExchangeFieldValues} = await import('../fieldsService.ts');

        await expect(saveExchangeFieldValues('exchange-1', request, '"set-1:7"'))
            .rejects.toEqual({errorMessage: 'refused', reasonCode: undefined});
    });

    it('throws a demand for a version it did not state rather than swallowing it', async () =>
    {
        patch.mockRejectedValueOnce(refusal(428, 'FIELDS_PRECONDITION_REQUIRED'));
        const {saveExchangeFieldValues} = await import('../fieldsService.ts');

        await expect(saveExchangeFieldValues('exchange-1', request, '"set-1:7"')).rejects.toBeTruthy();
    });

    it('leaves no unconditioned save on the module for a caller to reach for', async () =>
    {
        const module = await import('../fieldsService.ts');

        expect(Object.keys(module)).not.toContain('setExchangeFieldValues');
    });
});
