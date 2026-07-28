import {
    CreateDocumentLibraryEntryRequest,
    DocumentLibraryEntryDto,
    DocumentLibraryEntrySummaryDto,
    UpdateDocumentLibraryEntryRequest,
} from "../app/models/models.tsx";
import {
    createDocumentLibraryEntry,
    deleteDocumentLibraryEntry,
    downloadDocumentLibraryFile,
    listDocumentLibraryEntries,
    patchDocumentLibraryEntryPublished,
    patchDocumentLibraryEntryStatus,
    updateDocumentLibraryEntry,
    uploadDocumentLibraryFile,
} from "./documentLibraryService.ts";

const requirePlatformDocument = <T extends DocumentLibraryEntrySummaryDto>(entry: T): T =>
{
    if (entry.scope !== "APP")
        throw new Error("Platform Administration can manage only APP-scoped documents.");
    return entry;
};

export const listPlatformDocuments = async (): Promise<DocumentLibraryEntrySummaryDto[]> =>
{
    const entries = await listDocumentLibraryEntries({scope: "APP"});
    entries.forEach(requirePlatformDocument);
    return entries;
};

export const createPlatformDocument = async (
    request: Omit<CreateDocumentLibraryEntryRequest, "scope">,
): Promise<DocumentLibraryEntryDto> =>
    requirePlatformDocument(await createDocumentLibraryEntry({...request, scope: "APP"}));

export const updatePlatformDocument = async (
    entry: DocumentLibraryEntrySummaryDto,
    request: UpdateDocumentLibraryEntryRequest,
): Promise<DocumentLibraryEntryDto> =>
{
    requirePlatformDocument(entry);
    return requirePlatformDocument(await updateDocumentLibraryEntry(entry.id, request));
};

export const setPlatformDocumentPublished = async (
    entry: DocumentLibraryEntrySummaryDto,
): Promise<DocumentLibraryEntryDto> =>
{
    requirePlatformDocument(entry);
    return requirePlatformDocument(await patchDocumentLibraryEntryPublished(entry.id, {
        isPublished: !entry.isPublished,
    }));
};

export const setPlatformDocumentActive = async (
    entry: DocumentLibraryEntrySummaryDto,
): Promise<DocumentLibraryEntryDto> =>
{
    requirePlatformDocument(entry);
    return requirePlatformDocument(await patchDocumentLibraryEntryStatus(entry.id, {
        isActive: !entry.isActive,
    }));
};

export const uploadPlatformDocumentFile = async (
    entry: DocumentLibraryEntrySummaryDto,
    file: File,
    extension: string,
): Promise<DocumentLibraryEntryDto> =>
{
    requirePlatformDocument(entry);
    return requirePlatformDocument(await uploadDocumentLibraryFile(entry.id, file, extension));
};

export const downloadPlatformDocumentFile = (
    entry: DocumentLibraryEntrySummaryDto,
): Promise<void> =>
{
    requirePlatformDocument(entry);
    return downloadDocumentLibraryFile(entry.id, entry.fileName ?? entry.title);
};

export const deletePlatformDocument = (
    entry: DocumentLibraryEntrySummaryDto,
): Promise<void> =>
{
    requirePlatformDocument(entry);
    return deleteDocumentLibraryEntry(entry.id);
};
