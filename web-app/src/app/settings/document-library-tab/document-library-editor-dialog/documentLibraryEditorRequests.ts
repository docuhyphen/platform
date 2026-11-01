import {
    CreateDocumentLibraryEntryRequest,
    DocumentLibraryEntrySummaryDto,
    DocumentLibraryScope,
    UpdateDocumentLibraryEntryRequest,
} from '../../../models/models.tsx';
import {createDocumentLibraryEntry, updateDocumentLibraryEntry} from '../../../../services/documentLibraryService.ts';
import {createPlatformDocument, updatePlatformDocument} from '../../../../services/platformDocumentLibraryService.ts';

export interface DocumentLibraryEditorValues
{
    title: string;
    description: string;
    tags: string[];
    restrictType: boolean;
    restrictedType: string | undefined;
    required: boolean;
}

export interface DocumentLibraryEditorTarget
{
    entry?: DocumentLibraryEntrySummaryDto;
    scope: DocumentLibraryScope;
    enforcedScope?: DocumentLibraryScope;
}

const toEntryFields = (values: DocumentLibraryEditorValues) => ({
    title: values.title.trim(),
    description: values.description.trim() || undefined,
    generalTags: values.tags,
    restrictType: values.restrictType,
    restrictedType: values.restrictType ? values.restrictedType : undefined,
    required: values.required,
});

export const saveDocumentLibraryEntry = async (
    {entry, scope, enforcedScope}: DocumentLibraryEditorTarget,
    values: DocumentLibraryEditorValues,
): Promise<void> =>
{
    const fields = toEntryFields(values);
    if (entry)
    {
        const request: UpdateDocumentLibraryEntryRequest = fields;
        if (enforcedScope === 'APP')
            await updatePlatformDocument(entry, request);
        else
            await updateDocumentLibraryEntry(entry.id, request);
        return;
    }
    const request: CreateDocumentLibraryEntryRequest = {...fields, scope};
    if (enforcedScope === 'APP')
        await createPlatformDocument(request);
    else
        await createDocumentLibraryEntry(request);
};
