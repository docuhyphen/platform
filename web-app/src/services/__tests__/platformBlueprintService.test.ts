import {beforeEach, describe, expect, it, vi} from "vitest";
import {BlueprintDefinitionSummaryDto} from "../../app/models/models.tsx";

const listBlueprints = vi.fn();
const patchBlueprintPublished = vi.fn();
const patchBlueprintStatus = vi.fn();
const deleteBlueprint = vi.fn();

vi.mock("../blueprintService.ts", () => ({
    listBlueprints: (...args: unknown[]) => listBlueprints(...args),
    patchBlueprintPublished: (...args: unknown[]) => patchBlueprintPublished(...args),
    patchBlueprintStatus: (...args: unknown[]) => patchBlueprintStatus(...args),
    deleteBlueprint: (...args: unknown[]) => deleteBlueprint(...args),
}));

const blueprint = (scope: "APP" | "ORG"): BlueprintDefinitionSummaryDto => ({
    id: `blueprint-${scope}`,
    name: `${scope} Blueprint`,
    scope,
    isActive: false,
    isPublished: false,
    isTemplate: scope === "APP",
    generalTags: [],
    configJson: "{}",
    exchangeDocuments: [],
    participants: [],
    createdAt: "2026-07-28T00:00:00Z",
    updatedAt: "2026-07-28T00:00:00Z",
});

describe("platformBlueprintService", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("lists only the APP Blueprint scope", async () =>
    {
        listBlueprints.mockResolvedValueOnce([blueprint("APP")]);
        const {listPlatformBlueprints} = await import("../platformBlueprintService.ts");

        const result = await listPlatformBlueprints();

        expect(listBlueprints).toHaveBeenCalledWith({scope: "APP"});
        expect(result).toHaveLength(1);
    });

    it("rejects a response containing an organization Blueprint", async () =>
    {
        listBlueprints.mockResolvedValueOnce([blueprint("ORG")]);
        const {listPlatformBlueprints} = await import("../platformBlueprintService.ts");

        await expect(listPlatformBlueprints()).rejects.toThrow("only APP-scoped");
    });

    it("does not mutate a tenant-scoped Blueprint", async () =>
    {
        const {
            deletePlatformBlueprint,
            setPlatformBlueprintActive,
            setPlatformBlueprintPublished,
        } = await import("../platformBlueprintService.ts");
        const tenantBlueprint = blueprint("ORG");

        expect(() => setPlatformBlueprintActive(tenantBlueprint)).toThrow("only APP-scoped");
        expect(() => setPlatformBlueprintPublished(tenantBlueprint)).toThrow("only APP-scoped");
        expect(() => deletePlatformBlueprint(tenantBlueprint)).toThrow("only APP-scoped");
        expect(patchBlueprintStatus).not.toHaveBeenCalled();
        expect(patchBlueprintPublished).not.toHaveBeenCalled();
        expect(deleteBlueprint).not.toHaveBeenCalled();
    });
});
