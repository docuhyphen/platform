import {beforeEach, describe, expect, it, vi} from "vitest";
import {CommunicationSummaryDto} from "../../app/models/models.tsx";

const listCommunications = vi.fn();
const getCommunication = vi.fn();
const patchCommunicationPublished = vi.fn();
const patchCommunicationStatus = vi.fn();
const deleteCommunication = vi.fn();

vi.mock("../communicationService.ts", () => ({
    listCommunications: (...args: unknown[]) => listCommunications(...args),
    getCommunication: (...args: unknown[]) => getCommunication(...args),
    patchCommunicationPublished: (...args: unknown[]) => patchCommunicationPublished(...args),
    patchCommunicationStatus: (...args: unknown[]) => patchCommunicationStatus(...args),
    deleteCommunication: (...args: unknown[]) => deleteCommunication(...args),
}));

const communication = (scope: "PLATFORM" | "ORG"): CommunicationSummaryDto => ({
    id: `communication-${scope}`,
    name: `${scope} communication`,
    scope,
    subject: "Subject",
    isActive: false,
    isPublished: false,
    isTemplate: scope === "PLATFORM",
    generalTags: [],
    createdAt: "2026-07-28T00:00:00Z",
    updatedAt: "2026-07-28T00:00:00Z",
});

describe("platformCommunicationService", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("lists only the PLATFORM communication scope", async () =>
    {
        listCommunications.mockResolvedValueOnce([communication("PLATFORM")]);
        const {listPlatformCommunications} = await import("../platformCommunicationService.ts");

        const result = await listPlatformCommunications();

        expect(listCommunications).toHaveBeenCalledWith({scope: "PLATFORM"});
        expect(result).toHaveLength(1);
    });

    it("rejects a list containing an organization communication", async () =>
    {
        listCommunications.mockResolvedValueOnce([communication("ORG")]);
        const {listPlatformCommunications} = await import("../platformCommunicationService.ts");

        await expect(listPlatformCommunications()).rejects.toThrow("only PLATFORM-scoped");
    });

    it("does not read or mutate a tenant-scoped communication", async () =>
    {
        const {
            deletePlatformCommunication,
            getPlatformCommunication,
            setPlatformCommunicationActive,
            setPlatformCommunicationPublished,
        } = await import("../platformCommunicationService.ts");
        const tenantCommunication = communication("ORG");

        await expect(getPlatformCommunication(tenantCommunication)).rejects.toThrow("only PLATFORM-scoped");
        expect(() => setPlatformCommunicationActive(tenantCommunication)).toThrow("only PLATFORM-scoped");
        expect(() => setPlatformCommunicationPublished(tenantCommunication)).toThrow("only PLATFORM-scoped");
        expect(() => deletePlatformCommunication(tenantCommunication)).toThrow("only PLATFORM-scoped");
        expect(getCommunication).not.toHaveBeenCalled();
        expect(patchCommunicationStatus).not.toHaveBeenCalled();
        expect(patchCommunicationPublished).not.toHaveBeenCalled();
        expect(deleteCommunication).not.toHaveBeenCalled();
    });
});
