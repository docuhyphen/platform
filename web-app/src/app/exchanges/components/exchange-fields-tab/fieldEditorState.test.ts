import {describe, expect, it} from 'vitest';
import {
    FieldDataClassification,
    FieldValueDto,
    FieldValueType,
    SchemaFieldBindingDto,
} from '../../../models/models';
import {mergeFieldValueEdits, storedFieldValues} from './fieldEditorState';

const binding = (overrides: Partial<SchemaFieldBindingDto>): SchemaFieldBindingDto => ({
    id: `binding-${overrides.fieldContractId}`,
    fieldContractId: 'contract',
    fieldDefinitionId: 'definition',
    namespace: 'process',
    fieldKey: 'note',
    label: 'Note',
    valueType: FieldValueType.SHORT_TEXT,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [],
    ...overrides,
});

const storedValue = (fieldContractId: string, value: unknown): FieldValueDto => ({
    fieldContractId,
    namespace: 'process',
    fieldKey: 'note',
    label: 'Note',
    valueType: FieldValueType.SHORT_TEXT,
    isEmpty: value === null || value === undefined || value === '',
    value,
});

describe('storedFieldValues', () =>
{
    it('gives every binding a starting value, empty where nothing is stored for it', () =>
    {
        const bindings = [binding({fieldContractId: 'answered'}), binding({fieldContractId: 'unanswered'})];

        const stored = storedFieldValues(bindings, [storedValue('answered', 'recorded note')]);

        expect(stored).toEqual({answered: 'recorded note', unanswered: undefined});
        expect(Object.keys(stored)).toEqual(['answered', 'unanswered']);
    });

    it('starts a binding stored as empty from nothing rather than from its empty reading', () =>
    {
        const bindings = [binding({fieldContractId: 'cleared'})];

        const stored = storedFieldValues(bindings, [storedValue('cleared', '')]);

        expect(Object.keys(stored)).toEqual(['cleared']);
        expect(stored.cleared).toBeUndefined();
    });
});

describe('mergeFieldValueEdits', () =>
{
    it('follows the stored value of a binding the responder has not entered anything for', () =>
    {
        const shown = mergeFieldValueEdits(
            {untouched: 'value stored since the form loaded', entered: 'was stored'},
            {entered: 'responder is typing'},
        );

        expect(shown.untouched).toBe('value stored since the form loaded');
    });

    it('keeps what the responder entered when the stored values are reloaded underneath them', () =>
    {
        const edits = {entered: 'responder is typing'};

        const shown = mergeFieldValueEdits({entered: 'stored by someone else'}, edits);

        expect(shown.entered).toBe('responder is typing');
    });

    it('keeps a binding the responder cleared cleared, rather than restoring what is stored', () =>
    {
        const shown = mergeFieldValueEdits({cleared: 'stored note'}, {cleared: undefined});

        expect(shown.cleared).toBeUndefined();
        expect('cleared' in shown).toBe(true);
    });
});
