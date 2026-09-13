/** @vitest-environment jsdom */
import {afterEach, describe, expect, it, vi} from 'vitest';
import {cleanup, render, screen, waitFor} from '@testing-library/react';
import {
    ExchangeDetailedDto,
    ExchangeStatus,
    FieldDataClassification,
    FieldLifecycleStatus,
    FieldScopeKind,
    FieldValueType,
    SchemaAssignmentDto,
    SchemaAssignmentSource,
    SchemaDefinitionDto,
    SchemaFieldBindingDto,
} from '../../../models/models';
import {getExchangeSchema, getResolvedSchema, listSchemas} from '../../../../services/fieldsService';
import ExchangeFieldsTab from './ExchangeFieldsTab';

vi.mock('../../../../services/fieldsService', () => ({
    getExchangeSchema: vi.fn(),
    getResolvedSchema: vi.fn(),
    listSchemas: vi.fn(),
    assignExchangeSchema: vi.fn(),
    saveExchangeFieldValues: vi.fn(),
}));

const NOTE_CONTRACT = 'contract-recorded-note';
const OPTIONS_CONTRACT = 'contract-recorded-options';

const question = (
    fieldContractId: string,
    label: string,
    displayOrder: number,
    valueType = FieldValueType.SHORT_TEXT,
): SchemaFieldBindingDto => ({
    id: `binding-${fieldContractId}`,
    fieldContractId,
    fieldDefinitionId: `definition-${fieldContractId}`,
    namespace: 'process',
    fieldKey: fieldContractId,
    label,
    valueType,
    displayOrder,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
});

/** What the Exchange answers with: the questions asked of this caller and the answers held for them. */
const assignmentOf = (bindings: SchemaFieldBindingDto[]): SchemaAssignmentDto => ({
    id: 'assignment-1',
    resourceType: 'EXCHANGE',
    resourceId: 'exchange-1',
    schemaVersionId: 'version-1',
    schemaDefinitionId: 'definition-1',
    schemaKey: 'process-data',
    displayName: 'Process data',
    versionNumber: 1,
    assignmentSource: SchemaAssignmentSource.MANUAL,
    assignedAt: '2026-01-01T00:00:00Z',
    bindings,
    fields: bindings.map(binding => ({
        fieldContractId: binding.fieldContractId,
        schemaFieldBindingId: binding.id,
        namespace: binding.namespace,
        fieldKey: binding.fieldKey,
        label: binding.label,
        valueType: binding.valueType,
        isEmpty: true,
        value: null,
    })),
    etag: '"set-1:3"',
});

const exchange = (status: ExchangeStatus) =>
    ({id: 'exchange-1', status} as ExchangeDetailedDto);

const NOTHING_ASSIGNABLE = 'No published schemas are available for this organization yet.';

/** A published schema written for one kind of resource, as the schema list returns it. */
const publishedSchema = (targetResourceType: string): SchemaDefinitionDto => ({
    id: `schema-${targetResourceType}`,
    scopeKind: FieldScopeKind.ORGANIZATION,
    namespace: 'process',
    schemaKey: 'collected-data',
    displayName: 'Collected data',
    targetResourceType,
    status: FieldLifecycleStatus.PUBLISHED,
    latestPublishedVersion: {
        id: 'version-1',
        schemaDefinitionId: `schema-${targetResourceType}`,
        versionNumber: 1,
        status: FieldLifecycleStatus.PUBLISHED,
        bindings: [],
        createdAt: '2026-01-01T00:00:00Z',
    },
    createdAt: '2026-01-01T00:00:00Z',
});

const bothQuestions = [
    question(NOTE_CONTRACT, 'Recorded note', 0),
    question(OPTIONS_CONTRACT, 'Recorded options', 1, FieldValueType.MULTI_SELECT),
];

afterEach(() =>
{
    cleanup();
    vi.mocked(getExchangeSchema).mockReset();
    vi.mocked(getResolvedSchema).mockReset();
    vi.mocked(listSchemas).mockReset();
});

describe('ExchangeFieldsTab', () =>
{
    it('builds an editor for every question the Exchange itself describes', async () =>
    {
        vi.mocked(getExchangeSchema).mockResolvedValue(assignmentOf(bothQuestions));

        render(<ExchangeFieldsTab exchange={exchange(ExchangeStatus.INITIATED)}/>);

        await waitFor(() => expect(document.getElementById('exchange-fields-form-sections')).toBeTruthy());
        expect(document.getElementById(`exchange-field-${NOTE_CONTRACT}`)).toBeTruthy();
        expect(document.getElementById(`exchange-field-${OPTIONS_CONTRACT}`)).toBeTruthy();
        expect(screen.getByText('Recorded note')).toBeTruthy();
    });

    it('reads the questions and their answers in one request, never the schema configuration', async () =>
    {
        vi.mocked(getExchangeSchema).mockResolvedValue(assignmentOf(bothQuestions));

        render(<ExchangeFieldsTab exchange={exchange(ExchangeStatus.INITIATED)}/>);

        await waitFor(() => expect(document.getElementById('exchange-fields-form-sections')).toBeTruthy());
        expect(getExchangeSchema).toHaveBeenCalledTimes(1);
        expect(getResolvedSchema).not.toHaveBeenCalled();
    });

    it('shows the recorded answers as read-only once the Exchange is no longer a draft', async () =>
    {
        vi.mocked(getExchangeSchema).mockResolvedValue(assignmentOf(bothQuestions));

        render(<ExchangeFieldsTab exchange={exchange(ExchangeStatus.ACCEPTED_STARTED)}/>);

        await waitFor(() => expect(document.getElementById('exchange-fields-readonly-sections')).toBeTruthy());
        expect(screen.getByText('Recorded options')).toBeTruthy();
        expect(document.getElementById('exchange-fields-save-btn')).toBeNull();
        expect(getResolvedSchema).not.toHaveBeenCalled();
    });

    it('tells a caller nothing was shared when the Exchange describes no question to them', async () =>
    {
        vi.mocked(getExchangeSchema).mockResolvedValue(assignmentOf([]));

        render(<ExchangeFieldsTab exchange={exchange(ExchangeStatus.INITIATED)}/>);

        await waitFor(() => expect(document.getElementById('exchange-fields-no-visible-fields')).toBeTruthy());
        expect(document.getElementById('exchange-fields-form-sections')).toBeNull();
        expect(document.getElementById('exchange-fields-save-btn')).toBeNull();
    });

    it('offers the assignable schemas while no schema governs a draft Exchange', async () =>
    {
        vi.mocked(getExchangeSchema).mockResolvedValue(null);
        vi.mocked(listSchemas).mockResolvedValue([]);

        render(<ExchangeFieldsTab exchange={exchange(ExchangeStatus.INITIATED)}/>);

        await waitFor(() => expect(document.getElementById('exchange-schema-select')).toBeTruthy());
        expect(listSchemas).toHaveBeenCalledTimes(1);
        expect(getResolvedSchema).not.toHaveBeenCalled();
    });

    it('offers a published schema written for an Exchange', async () =>
    {
        vi.mocked(getExchangeSchema).mockResolvedValue(null);
        vi.mocked(listSchemas).mockResolvedValue([publishedSchema('EXCHANGE')]);

        render(<ExchangeFieldsTab exchange={exchange(ExchangeStatus.INITIATED)}/>);

        await waitFor(() => expect(document.getElementById('exchange-schema-select')).toBeTruthy());
        expect(document.getElementById('exchange-schema-select')?.getAttribute('disabled')).toBeNull();
        expect(screen.queryByText(NOTHING_ASSIGNABLE)).toBeNull();
    });

    it('does not offer a schema written for another kind of resource', async () =>
    {
        vi.mocked(getExchangeSchema).mockResolvedValue(null);
        vi.mocked(listSchemas).mockResolvedValue([publishedSchema('INFORMATION_REQUEST')]);

        render(<ExchangeFieldsTab exchange={exchange(ExchangeStatus.INITIATED)}/>);

        await waitFor(() => expect(document.getElementById('exchange-schema-select')).toBeTruthy());
        expect(document.getElementById('exchange-schema-select')?.getAttribute('disabled')).not.toBeNull();
        expect(screen.getByText(NOTHING_ASSIGNABLE)).toBeTruthy();
    });
});
