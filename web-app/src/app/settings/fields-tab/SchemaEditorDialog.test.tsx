/** @vitest-environment jsdom */
import {afterEach, describe, expect, it, vi} from 'vitest';
import {cleanup, fireEvent, render, screen, waitFor} from '@testing-library/react';
import {
    FieldDataClassification,
    FieldLifecycleStatus,
    FieldScopeKind,
    FieldValueType,
    SchemaDefinitionDto,
    SchemaFieldBindingDto,
    SchemaVersionDto,
} from '../../models/models';
import {createSchema, updateSchemaDraftBindings} from '../../../services/fieldsService';
import {createPlatformSchema} from '../../../services/platformFieldsService.ts';
import SchemaEditorDialog from './SchemaEditorDialog';

vi.mock('../../../services/fieldsService', () => ({
    createSchema: vi.fn(),
    updateSchemaDraftBindings: vi.fn(),
}));

vi.mock('../../../services/platformFieldsService.ts', () => ({
    createPlatformSchema: vi.fn(),
    updatePlatformSchemaBindings: vi.fn(),
}));

const configuredBinding: SchemaFieldBindingDto = {
    id: 'binding-reference',
    fieldContractId: 'contract-reference',
    fieldDefinitionId: 'field-reference',
    namespace: 'process',
    fieldKey: 'reference-code',
    label: 'Reference code',
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    section: 'Identity',
    isRequired: false,
    isReadOnly: true,
    defaultValueJson: '"Standard reference"',
    visibility: FieldDataClassification.CONFIDENTIAL,
    constraints: {},
    options: [],
};

const draftVersion: SchemaVersionDto = {
    id: 'version-2',
    schemaDefinitionId: 'schema-1',
    versionNumber: 2,
    status: FieldLifecycleStatus.DRAFT,
    bindings: [configuredBinding],
    createdAt: Date.parse('2026-01-01T00:00:00Z'),
};

const schema: SchemaDefinitionDto = {
    id: 'schema-1',
    scopeKind: FieldScopeKind.ORGANIZATION,
    namespace: 'process',
    schemaKey: 'process-data',
    displayName: 'Process data',
    targetResourceType: 'EXCHANGE',
    status: FieldLifecycleStatus.PUBLISHED,
    draftVersion,
    createdAt: Date.parse('2026-01-01T00:00:00Z'),
};

afterEach(() => {
    cleanup();
    vi.mocked(updateSchemaDraftBindings).mockReset();
    vi.mocked(createSchema).mockReset();
    vi.mocked(createPlatformSchema).mockReset();
});

const inputById = (id: string): HTMLElement => {
    const input = document.getElementById(id);
    if (!input) throw new Error(`No input with id ${id}`);
    return input;
};

const fillNewSchemaIdentity = async () => {
    await screen.findByText('Create');
    fireEvent.change(inputById('schema-namespace'), {target: {value: 'process'}});
    fireEvent.change(inputById('schema-key'), {target: {value: 'request-data'}});
    fireEvent.change(inputById('schema-display-name'), {target: {value: 'Request data'}});
};

describe('SchemaEditorDialog', () => {
    it('saving a new version keeps the default, visibility and section each field already has', async () => {
        vi.mocked(updateSchemaDraftBindings).mockResolvedValue(draftVersion);

        render(<SchemaEditorDialog open={true}
                                   onClose={vi.fn()}
                                   onSaved={vi.fn()}
                                   definitions={[]}
                                   schema={schema}/>);
        fireEvent.click(await screen.findByText('Save draft'));

        await waitFor(() => expect(updateSchemaDraftBindings).toHaveBeenCalledTimes(1));
        expect(vi.mocked(updateSchemaDraftBindings).mock.calls[0]).toEqual([
            'schema-1',
            {
                bindings: [{
                    fieldContractId: 'contract-reference',
                    displayOrder: 0,
                    section: 'Identity',
                    isRequired: false,
                    isReadOnly: true,
                    defaultValueJson: '"Standard reference"',
                    visibility: FieldDataClassification.CONFIDENTIAL,
                }],
            },
        ]);
    });

    it('shows the reason the server gave when it refuses a default value', async () => {
        vi.mocked(updateSchemaDraftBindings).mockRejectedValue({
            errorMessage: 'Default value for Reference code is not valid: value too long',
        });

        render(<SchemaEditorDialog open={true}
                                   onClose={vi.fn()}
                                   onSaved={vi.fn()}
                                   definitions={[]}
                                   schema={schema}/>);
        fireEvent.click(await screen.findByText('Save draft'));

        expect(await screen.findByText('Default value for Reference code is not valid: value too long'))
            .toBeTruthy();
    });

    it('a new schema is made for Exchanges unless Information Requests is chosen', async () => {
        vi.mocked(createSchema).mockResolvedValue({...schema, draftVersion: undefined});

        render(<SchemaEditorDialog open={true}
                                   onClose={vi.fn()}
                                   onSaved={vi.fn()}
                                   definitions={[]}/>);
        await fillNewSchemaIdentity();
        fireEvent.click(screen.getByText('Create'));

        await waitFor(() => expect(createSchema).toHaveBeenCalledTimes(1));
        expect(vi.mocked(createSchema).mock.calls[0][0]).toMatchObject({
            namespace: 'process',
            schemaKey: 'request-data',
            displayName: 'Request data',
            targetResourceType: 'EXCHANGE',
        });
    });

    it('a schema made for Information Requests is created with that target', async () => {
        vi.mocked(createSchema).mockResolvedValue({...schema, targetResourceType: 'INFORMATION_REQUEST'});

        render(<SchemaEditorDialog open={true}
                                   onClose={vi.fn()}
                                   onSaved={vi.fn()}
                                   definitions={[]}/>);
        await fillNewSchemaIdentity();
        fireEvent.click(screen.getByLabelText('Information Requests'));
        fireEvent.click(screen.getByText('Create'));

        await waitFor(() => expect(createSchema).toHaveBeenCalledTimes(1));
        expect(vi.mocked(createSchema).mock.calls[0][0]).toMatchObject({targetResourceType: 'INFORMATION_REQUEST'});
    });

    it('a platform schema made for Information Requests keeps that target', async () => {
        vi.mocked(createPlatformSchema).mockResolvedValue({
            ...schema,
            scopeKind: FieldScopeKind.PLATFORM,
            targetResourceType: 'INFORMATION_REQUEST',
        });

        render(<SchemaEditorDialog open={true}
                                   onClose={vi.fn()}
                                   onSaved={vi.fn()}
                                   definitions={[]}
                                   enforcedScope={FieldScopeKind.PLATFORM}/>);
        await fillNewSchemaIdentity();
        fireEvent.click(screen.getByLabelText('Information Requests'));
        fireEvent.click(screen.getByText('Create'));

        await waitFor(() => expect(createPlatformSchema).toHaveBeenCalledTimes(1));
        expect(vi.mocked(createPlatformSchema).mock.calls[0][0]).toMatchObject({targetResourceType: 'INFORMATION_REQUEST'});
    });

    it('editing an existing schema offers no target choice', async () => {
        render(<SchemaEditorDialog open={true}
                                   onClose={vi.fn()}
                                   onSaved={vi.fn()}
                                   definitions={[]}
                                   schema={schema}/>);

        await screen.findByText('Save draft');
        expect(screen.queryByLabelText('Information Requests')).toBeNull();
    });
});
