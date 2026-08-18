/** @vitest-environment jsdom */
import {cleanup, fireEvent, render} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {
    FieldLifecycleStatus,
    FieldScopeKind,
    SchemaDefinitionDto,
} from '../../../../models/models';
import BusinessSchemaCombobox from './BusinessSchemaCombobox';

const schema = (
    id: string,
    displayName: string,
    namespace: string,
    schemaKey: string,
): SchemaDefinitionDto => ({
    id,
    displayName,
    namespace,
    schemaKey,
    scopeKind: FieldScopeKind.ORGANIZATION,
    targetResourceType: 'EXCHANGE',
    status: FieldLifecycleStatus.PUBLISHED,
    createdAt: '2026-08-17T00:00:00Z',
});

afterEach(cleanup);

describe('BusinessSchemaCombobox', () =>
{
    it('starts with an empty searchable input when no schema is selected', () =>
    {
        const {container} = render(
            <BusinessSchemaCombobox
                schemas={[schema('claim', 'Insurance Claim', 'insurance', 'claim-case')]}
                onSchemaChange={vi.fn()}
            />,
        );

        expect(container.querySelector<HTMLInputElement>('#exchange-initiation-schema-select')?.value)
            .toBe('');
    });

    it('filters schemas by display name and namespaced key', () =>
    {
        const schemas = [
            schema('claim', 'Insurance Claim', 'insurance', 'claim-case'),
            schema('loan', 'Loan Application', 'finance', 'loan-application'),
        ];
        const {container} = render(
            <BusinessSchemaCombobox
                schemas={schemas}
                onSchemaChange={vi.fn()}
            />,
        );
        const combobox = container.querySelector('#exchange-initiation-schema-select') as HTMLInputElement;

        fireEvent.input(combobox, {target: {value: 'finance:loan'}});
        fireEvent.click(combobox);

        expect(document.querySelector('#exchange-initiation-schema-option-loan')).toBeTruthy();
        expect(document.querySelector('#exchange-initiation-schema-option-claim')).toBeNull();
    });
});
