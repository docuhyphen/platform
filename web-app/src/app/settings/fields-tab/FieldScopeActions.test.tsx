/** @vitest-environment jsdom */
import {cleanup, render} from "@testing-library/react";
import {afterEach, describe, expect, it, vi} from "vitest";
import {
    FieldDefinitionDto,
    FieldLifecycleStatus,
    FieldScopeKind,
    SchemaDefinitionDto,
} from "../../models/models.tsx";
import FieldDefinitionsPanel from "./FieldDefinitionsPanel.tsx";
import SchemaCard from "./SchemaCard.tsx";

const definition = (scopeKind: FieldScopeKind): FieldDefinitionDto => ({
    id: `field-${scopeKind}`,
    scopeKind,
    namespace: "common",
    fieldKey: "reference",
    status: FieldLifecycleStatus.PUBLISHED,
    contractCount: 0,
    createdAt: "2026-07-28T00:00:00Z",
});

const schema = (scopeKind: FieldScopeKind): SchemaDefinitionDto => ({
    id: `schema-${scopeKind}`,
    scopeKind,
    namespace: "common",
    schemaKey: "case",
    displayName: "Case",
    targetResourceType: "EXCHANGE",
    status: FieldLifecycleStatus.PUBLISHED,
    createdAt: "2026-07-28T00:00:00Z",
});

afterEach(cleanup);

describe("Settings field scope actions", () =>
{
    it("shows actions only for organization Field records", () =>
    {
        const platform = definition(FieldScopeKind.PLATFORM);
        const organization = definition(FieldScopeKind.ORGANIZATION);
        render(
            <FieldDefinitionsPanel
                definitions={[platform, organization]}
                viewMode={"cards"}
                canManage={item => item.scopeKind === FieldScopeKind.ORGANIZATION}
                loading={false}
                error={null}
                onRetire={vi.fn()}/>,
        );

        expect(document.querySelector(`#field-def-menu-${platform.id}`)).toBeNull();
        expect(document.querySelector(`#field-def-menu-${organization.id}`)).toBeTruthy();
    });

    it("does not show schema actions for a platform record", () =>
    {
        const platform = schema(FieldScopeKind.PLATFORM);
        render(
            <SchemaCard
                schema={platform}
                canManage={false}
                viewMode={"cards"}
                onEdit={vi.fn()}
                onPublish={vi.fn()}
                onNewVersion={vi.fn()}
                onRetire={vi.fn()}/>,
        );

        expect(document.querySelector(`#schema-menu-${platform.id}`)).toBeNull();
    });
});
