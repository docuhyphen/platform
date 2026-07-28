import {beforeEach, describe, expect, it, vi} from "vitest";
import {WorkflowDefinitionSummaryDto} from "../../app/models/models.tsx";

const listWorkflowDefinitions = vi.fn();
const patchWorkflowDefinitionPublished = vi.fn();
const patchWorkflowDefinitionStatus = vi.fn();

vi.mock("../workflowService.ts", () => ({
    listWorkflowDefinitions: (...args: unknown[]) => listWorkflowDefinitions(...args),
    patchWorkflowDefinitionPublished: (...args: unknown[]) =>
        patchWorkflowDefinitionPublished(...args),
    patchWorkflowDefinitionStatus: (...args: unknown[]) => patchWorkflowDefinitionStatus(...args),
}));

const definition = (scope: "APP" | "ORG"): WorkflowDefinitionSummaryDto => ({
    id: `workflow-${scope}`,
    name: `${scope} workflow`,
    triggerEvent: "exchange.created",
    version: 1,
    isActive: false,
    isPublished: false,
    isTemplate: scope === "APP",
    scope,
    generalTags: [],
    createdAt: "2026-07-28T00:00:00Z",
});

describe("platformWorkflowService", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("lists only the APP workflow scope", async () =>
    {
        listWorkflowDefinitions.mockResolvedValueOnce([definition("APP")]);
        const {listPlatformWorkflowTemplates} = await import("../platformWorkflowService.ts");

        const result = await listPlatformWorkflowTemplates();

        expect(listWorkflowDefinitions).toHaveBeenCalledWith({scope: "APP"});
        expect(result).toHaveLength(1);
    });

    it("rejects a response containing an organization workflow", async () =>
    {
        listWorkflowDefinitions.mockResolvedValueOnce([definition("ORG")]);
        const {listPlatformWorkflowTemplates} = await import("../platformWorkflowService.ts");

        await expect(listPlatformWorkflowTemplates()).rejects.toThrow("tenant-scoped");
    });

    it("does not mutate a tenant-scoped workflow", async () =>
    {
        const {
            setPlatformWorkflowActive,
            setPlatformWorkflowPublished,
        } = await import("../platformWorkflowService.ts");
        const tenantDefinition = definition("ORG");

        expect(() => setPlatformWorkflowActive(tenantDefinition)).toThrow("tenant-scoped");
        expect(() => setPlatformWorkflowPublished(tenantDefinition)).toThrow("tenant-scoped");
        expect(patchWorkflowDefinitionStatus).not.toHaveBeenCalled();
        expect(patchWorkflowDefinitionPublished).not.toHaveBeenCalled();
    });
});
