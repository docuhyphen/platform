import {beforeEach, describe, expect, it, vi} from "vitest";
import {
    DocumentLibraryEntryDto,
    DocumentLibraryEntrySummaryDto,
} from "../../app/models/models.tsx";

const listDocumentLibraryEntries = vi.fn();
const createDocumentLibraryEntry = vi.fn();
const updateDocumentLibraryEntry = vi.fn();
const patchDocumentLibraryEntryPublished = vi.fn();
const patchDocumentLibraryEntryStatus = vi.fn();
const uploadDocumentLibraryFile = vi.fn();
const downloadDocumentLibraryFile = vi.fn();
const deleteDocumentLibraryEntry = vi.fn();

vi.mock("../documentLibraryService.ts", () => ({
    listDocumentLibraryEntries: (...args: unknown[]) => listDocumentLibraryEntries(...args),
    createDocumentLibraryEntry: (...args: unknown[]) => createDocumentLibraryEntry(...args),
    updateDocumentLibraryEntry: (...args: unknown[]) => updateDocumentLibraryEntry(...args),
    patchDocumentLibraryEntryPublished: (...args: unknown[]) => patchDocumentLibraryEntryPublished(...args),
    patchDocumentLibraryEntryStatus: (...args: unknown[]) => patchDocumentLibraryEntryStatus(...args),
    uploadDocumentLibraryFile: (...args: unknown[]) => uploadDocumentLibraryFile(...args),
    downloadDocumentLibraryFile: (...args: unknown[]) => downloadDocumentLibraryFile(...args),
    deleteDocumentLibraryEntry: (...args: unknown[]) => deleteDocumentLibraryEntry(...args),
}));

const documentEntry = (
    scope: "APP" | "ORG" | "PERSONAL",
): DocumentLibraryEntrySummaryDto => ({
    id: `document-${scope}`,
    title: `${scope} document`,
    scope,
    isPublished: false,
    isActive: true,
    hasFile: true,
    generalTags: [],
    createdAt: "2026-07-28T00:00:00Z",
    updatedAt: "2026-07-28T00:00:00Z",
});

describe("platformDocumentLibraryService", () =>
{
    beforeEach(() => vi.clearAllMocks());

    it("lists and creates only APP-scoped documents", async () =>
    {
        const platformDocument = documentEntry("APP") as DocumentLibraryEntryDto;
        listDocumentLibraryEntries.mockResolvedValueOnce([platformDocument]);
        createDocumentLibraryEntry.mockResolvedValueOnce(platformDocument);
        const {createPlatformDocument, listPlatformDocuments} =
            await import("../platformDocumentLibraryService.ts");

        await expect(listPlatformDocuments()).resolves.toEqual([platformDocument]);
        await expect(createPlatformDocument({title: "Platform"})).resolves.toEqual(platformDocument);

        expect(listDocumentLibraryEntries).toHaveBeenCalledWith({scope: "APP"});
        expect(createDocumentLibraryEntry).toHaveBeenCalledWith({title: "Platform", scope: "APP"});
    });

    it("rejects a list containing tenant content", async () =>
    {
        listDocumentLibraryEntries.mockResolvedValueOnce([documentEntry("ORG")]);
        const {listPlatformDocuments} = await import("../platformDocumentLibraryService.ts");

        await expect(listPlatformDocuments()).rejects.toThrow("only APP-scoped");
    });

    it("does not read or mutate a non-APP document", async () =>
    {
        const service = await import("../platformDocumentLibraryService.ts");
        const tenantDocument = documentEntry("ORG");
        const file = new File(["tenant"], "tenant.pdf", {type: "application/pdf"});

        await expect(service.setPlatformDocumentActive(tenantDocument)).rejects.toThrow("only APP-scoped");
        await expect(service.setPlatformDocumentPublished(tenantDocument)).rejects.toThrow("only APP-scoped");
        expect(() => service.deletePlatformDocument(tenantDocument)).toThrow("only APP-scoped");
        expect(() => service.downloadPlatformDocumentFile(tenantDocument)).toThrow("only APP-scoped");
        await expect(service.updatePlatformDocument(tenantDocument, {title: "No"}))
            .rejects.toThrow("only APP-scoped");
        await expect(service.uploadPlatformDocumentFile(tenantDocument, file, "pdf"))
            .rejects.toThrow("only APP-scoped");

        expect(updateDocumentLibraryEntry).not.toHaveBeenCalled();
        expect(patchDocumentLibraryEntryPublished).not.toHaveBeenCalled();
        expect(patchDocumentLibraryEntryStatus).not.toHaveBeenCalled();
        expect(uploadDocumentLibraryFile).not.toHaveBeenCalled();
        expect(downloadDocumentLibraryFile).not.toHaveBeenCalled();
        expect(deleteDocumentLibraryEntry).not.toHaveBeenCalled();
    });
});
