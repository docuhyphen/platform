import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    FieldDataClassification,
    FieldDefinitionDto,
    FieldLifecycleStatus,
    FieldScopeKind,
    FieldValueType,
    SchemaDefinitionDto,
} from "../../app/models/models.tsx";

const listFieldDefinitions = vi.fn();
const createFieldDefinition = vi.fn();
const retireFieldDefinition = vi.fn();
const listSchemas = vi.fn();
const getSchema = vi.fn();
const createSchema = vi.fn();
const updateSchemaDraftBindings = vi.fn();
const publishSchemaDraft = vi.fn();
const createSchemaDraftVersion = vi.fn();
const retireSchema = vi.fn();

vi.mock("../fieldsService.ts", () => ({
    listFieldDefinitions: (...args: unknown[]) => listFieldDefinitions(...args),
    createFieldDefinition: (...args: unknown[]) => createFieldDefinition(...args),
    retireFieldDefinition: (...args: unknown[]) => retireFieldDefinition(...args),
    listSchemas: (...args: unknown[]) => listSchemas(...args),
    getSchema: (...args: unknown[]) => getSchema(...args),
    createSchema: (...args: unknown[]) => createSchema(...args),
    updateSchemaDraftBindings: (...args: unknown[]) => updateSchemaDraftBindings(...args),
    publishSchemaDraft: (...args: unknown[]) => publishSchemaDraft(...args),
    createSchemaDraftVersion: (...args: unknown[]) => createSchemaDraftVersion(...args),
    retireSchema: (...args: unknown[]) => retireSchema(...args),
}));

const field = (scopeKind: FieldScopeKind): FieldDefinitionDto => ({
    id: `field-${scopeKind}`,
    scopeKind,
    namespace: "common",
    fieldKey: "reference",
    status: FieldLifecycleStatus.PUBLISHED,
    contractCount: 1,
    latestContract: {
        id: "contract",
        fieldDefinitionId: "field",
        contractVersion: 1,
        valueType: FieldValueType.SHORT_TEXT,
        typeContractVersion: 1,
        label: "Reference",
        constraints: {},
        options: [],
        dataClassification: FieldDataClassification.INTERNAL,
        isSearchable: false,
        isFilterable: false,
        isSortable: false,
        isReportable: false,
        createdAt: "2026-07-28T00:00:00Z",
    },
    createdAt: "2026-07-28T00:00:00Z",
});

const schema = (scopeKind: FieldScopeKind): SchemaDefinitionDto => ({
    id: `schema-${scopeKind}`,
    scopeKind,
    namespace: "common",
    schemaKey: "case",
    displayName: "Case",
    targetResourceType: "EXCHANGE",
    status: FieldLifecycleStatus.DRAFT,
    createdAt: "2026-07-28T00:00:00Z",
});

describe("platformFieldsService", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("uses explicit PLATFORM list and creation scope", async () =>
    {
        listFieldDefinitions.mockResolvedValueOnce([field(FieldScopeKind.PLATFORM)]);
        listSchemas.mockResolvedValueOnce([schema(FieldScopeKind.PLATFORM)]);
        createFieldDefinition.mockResolvedValueOnce(field(FieldScopeKind.PLATFORM));
        createSchema.mockResolvedValueOnce(schema(FieldScopeKind.PLATFORM));
        const service = await import("../platformFieldsService.ts");

        await service.listPlatformFields();
        await service.listPlatformSchemas();
        await service.createPlatformField({
            namespace: "common",
            fieldKey: "reference",
            contract: {valueType: FieldValueType.SHORT_TEXT, label: "Reference"},
        });
        await service.createPlatformSchema({
            namespace: "common",
            schemaKey: "case",
            displayName: "Case",
        });

        expect(listFieldDefinitions).toHaveBeenCalledWith({scopeKind: FieldScopeKind.PLATFORM});
        expect(listSchemas).toHaveBeenCalledWith({scopeKind: FieldScopeKind.PLATFORM});
        expect(createFieldDefinition).toHaveBeenCalledWith(expect.objectContaining({
            scopeKind: FieldScopeKind.PLATFORM,
        }));
        expect(createSchema).toHaveBeenCalledWith(expect.objectContaining({
            scopeKind: FieldScopeKind.PLATFORM,
        }));
    });

    it("rejects organization records before ID-based operations", async () =>
    {
        const service = await import("../platformFieldsService.ts");
        const organizationField = field(FieldScopeKind.ORGANIZATION);
        const organizationSchema = schema(FieldScopeKind.ORGANIZATION);

        await expect(service.retirePlatformField(organizationField)).rejects.toThrow("PLATFORM-scoped fields");
        await expect(service.getPlatformSchema(organizationSchema)).rejects.toThrow("PLATFORM-scoped schemas");
        expect(() => service.updatePlatformSchemaBindings(organizationSchema, []))
            .toThrow("PLATFORM-scoped schemas");
        expect(() => service.createPlatformSchemaDraftVersion(organizationSchema))
            .toThrow("PLATFORM-scoped schemas");
        await expect(service.publishPlatformSchema(organizationSchema)).rejects.toThrow("PLATFORM-scoped schemas");
        await expect(service.retirePlatformSchema(organizationSchema)).rejects.toThrow("PLATFORM-scoped schemas");

        expect(retireFieldDefinition).not.toHaveBeenCalled();
        expect(getSchema).not.toHaveBeenCalled();
        expect(updateSchemaDraftBindings).not.toHaveBeenCalled();
        expect(publishSchemaDraft).not.toHaveBeenCalled();
        expect(createSchemaDraftVersion).not.toHaveBeenCalled();
        expect(retireSchema).not.toHaveBeenCalled();
    });
});
