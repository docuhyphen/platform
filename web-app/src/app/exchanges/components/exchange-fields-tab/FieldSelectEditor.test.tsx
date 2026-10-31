/** @vitest-environment jsdom */
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {FieldDataClassification, FieldValueType, SchemaFieldBindingDto} from '../../../models/models';
import FieldSelectEditor from './FieldSelectEditor';

afterEach(cleanup);

const binding = (valueType: FieldValueType): SchemaFieldBindingDto => ({
    id: 'schema-binding',
    fieldContractId: 'field-contract',
    fieldDefinitionId: 'field-definition',
    namespace: 'process',
    fieldKey: 'record-choice',
    label: 'Record choice',
    valueType,
    displayOrder: 0,
    isRequired: false,
    isReadOnly: false,
    visibility: FieldDataClassification.INTERNAL,
    constraints: {},
    options: [{code: 'FIRST', label: 'First option'}, {code: 'SECOND', label: 'Second option'}],
});

const trigger = (container: HTMLElement) =>
    container.querySelector<HTMLButtonElement>('#process-choice');

const clearControl = () => screen.queryByRole('button', {name: /clear/i});

describe('FieldSelectEditor', () => {
    it('reports a chosen option', () => {
        const onChange = vi.fn();
        const {container} = render(
            <FieldSelectEditor id="process-choice"
                               binding={binding(FieldValueType.SINGLE_SELECT)}
                               value={undefined}
                               onChange={onChange}/>,
        );

        fireEvent.click(trigger(container) as HTMLButtonElement);
        fireEvent.click(screen.getByRole('option', {name: 'Second option'}));

        expect(onChange).toHaveBeenCalledWith('SECOND');
    });

    it('reports a cleared single choice as unanswered', () => {
        const onChange = vi.fn();
        render(
            <FieldSelectEditor id="process-choice"
                               binding={binding(FieldValueType.SINGLE_SELECT)}
                               value={'FIRST'}
                               onChange={onChange}/>,
        );

        fireEvent.click(clearControl() as HTMLElement);

        expect(onChange).toHaveBeenCalledWith(null);
    });

    it('offers no clear while a single choice is unanswered or read-only', () => {
        render(
            <FieldSelectEditor id="process-choice"
                               binding={binding(FieldValueType.SINGLE_SELECT)}
                               value={undefined}
                               onChange={vi.fn()}/>,
        );
        expect(clearControl()).toBeNull();

        cleanup();

        const {container} = render(
            <FieldSelectEditor id="process-choice"
                               binding={binding(FieldValueType.SINGLE_SELECT)}
                               value={'FIRST'}
                               disabled
                               onChange={vi.fn()}/>,
        );
        expect(clearControl()).toBeNull();
        expect(trigger(container)?.disabled).toBe(true);
    });

    it('leaves a multiple choice to be cleared by deselecting its options', () => {
        render(
            <FieldSelectEditor id="process-choice"
                               binding={binding(FieldValueType.MULTI_SELECT)}
                               value={['FIRST']}
                               onChange={vi.fn()}/>,
        );

        expect(clearControl()).toBeNull();
    });
});
