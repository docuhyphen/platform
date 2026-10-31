/** @vitest-environment jsdom */
import {afterEach, describe, expect, it, vi} from 'vitest';
import {cleanup, render, screen} from '@testing-library/react';
import {FieldLifecycleStatus, FieldScopeKind, SchemaDefinitionDto, SchemaTargetResourceType} from '../../models/models';
import SchemaCard from './SchemaCard';

const schemaFor = (targetResourceType: SchemaTargetResourceType): SchemaDefinitionDto => ({
    id: `schema-${targetResourceType.toLowerCase()}`,
    scopeKind: FieldScopeKind.ORGANIZATION,
    namespace: 'process',
    schemaKey: 'process-data',
    displayName: 'Process data',
    targetResourceType,
    status: FieldLifecycleStatus.PUBLISHED,
    createdAt: Date.parse('2026-01-01T00:00:00Z'),
});

const renderCard = (schema: SchemaDefinitionDto) =>
    render(<SchemaCard schema={schema}
                       canManage={false}
                       viewMode="cards"
                       onEdit={vi.fn()}
                       onPublish={vi.fn()}
                       onNewVersion={vi.fn()}
                       onRetire={vi.fn()}/>);

afterEach(() => cleanup());

describe('SchemaCard', () => {
    it('marks a schema made for Information Requests', () => {
        renderCard(schemaFor(SchemaTargetResourceType.INFORMATION_REQUEST));

        expect(screen.getByText('Information Requests')).toBeTruthy();
    });

    it('adds no target mark to an Exchange schema', () => {
        renderCard(schemaFor(SchemaTargetResourceType.EXCHANGE));

        expect(screen.queryByText('Information Requests')).toBeNull();
    });
});
