/** @vitest-environment jsdom */
import {afterEach, describe, expect, it, vi} from 'vitest';
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import {FieldDataClassification, FieldValueType} from '../../../models/models';
import {BindingDraft} from '../schemaBindingDrafts';
import SchemaBindingRow from './SchemaBindingRow';

const draft = (configured: Partial<BindingDraft> = {}): BindingDraft => ({
    fieldDefinitionId: 'field-reference',
    fieldContractId: 'contract-reference',
    label: 'Reference code',
    keyLabel: 'process:reference-code',
    valueType: FieldValueType.SHORT_TEXT,
    options: [],
    isRequired: false,
    isReadOnly: true,
    visibility: FieldDataClassification.INTERNAL,
    ...configured,
});

const renderRow = (binding: BindingDraft) => {
    const onChange = vi.fn();
    render(<SchemaBindingRow binding={binding}
                             index={0}
                             count={1}
                             onMove={vi.fn()}
                             onRemove={vi.fn()}
                             onChange={onChange}/>);
    return onChange;
};

const element = (id: string) => document.getElementById(id) as HTMLElement;

afterEach(() => cleanup());

describe('SchemaBindingRow', () => {
    it('records a default value in the canonical form the field type stores', () => {
        const onChange = renderRow(draft());

        fireEvent.change(element('binding-default-0'), {target: {value: 'Standard reference'}});

        expect(onChange).toHaveBeenCalledWith({defaultValueJson: '"Standard reference"'});
    });

    it('shows the default value the binding already holds', () => {
        renderRow(draft({defaultValueJson: '"Standard reference"'}));

        expect((element('binding-default-0') as HTMLInputElement).value).toBe('Standard reference');
    });

    it('removes the default value when it is cleared', () => {
        const onChange = renderRow(draft({defaultValueJson: '"Standard reference"'}));

        fireEvent.click(element('binding-default-clear-0'));

        expect(onChange).toHaveBeenCalledWith({defaultValueJson: undefined});
    });

    it('records a Yes or No default as a JSON Boolean', async () => {
        const onChange = renderRow(draft({valueType: FieldValueType.BOOLEAN}));

        fireEvent.click(element('binding-default-0'));
        fireEvent.click(await screen.findByRole('option', {name: 'Yes'}));

        expect(onChange).toHaveBeenCalledWith({defaultValueJson: 'true'});
    });

    it('changes who may see the field on this schema', async () => {
        const onChange = renderRow(draft());

        fireEvent.click(element('binding-visibility-0'));
        fireEvent.click(await screen.findByRole('option', {name: 'Public'}));

        expect(onChange).toHaveBeenCalledWith({visibility: FieldDataClassification.PUBLIC});
    });

    it('places the field in a named section, or in none when the name is cleared', () => {
        const onChange = renderRow(draft({section: 'Identity'}));

        fireEvent.change(element('binding-section-0'), {target: {value: 'Review'}});
        fireEvent.change(element('binding-section-0'), {target: {value: ''}});

        expect(onChange).toHaveBeenNthCalledWith(1, {section: 'Review'});
        expect(onChange).toHaveBeenNthCalledWith(2, {section: undefined});
    });
});
