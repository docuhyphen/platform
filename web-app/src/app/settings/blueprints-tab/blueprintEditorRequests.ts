import {
    BlueprintDocumentConfig,
    BlueprintScope,
    CreateBlueprintRequest,
    DocumentLibraryEntrySummaryDto,
    UpdateBlueprintRequest,
} from '../../models/models.tsx';
import {templateVersionChange} from './blueprint-information-request-tab/blueprintTemplateVersionChange.ts';
import {BlueprintEditorValues} from './blueprintEditorTypes.ts';

export const buildUpdateBlueprintRequest = (
    values: BlueprintEditorValues,
    previousTemplateVersionId: string | undefined,
): UpdateBlueprintRequest => ({
    name: values.name.trim(),
    summary: values.summary.trim() || undefined,
    description: values.description.trim() || undefined,
    configJson: JSON.stringify(values.config),
    exchangeDocuments: values.documents,
    generalTags: values.tags,
    schemaDefinitionId: values.schemaDefinitionId,
    fieldDefaults: values.fieldDefaults,
    ...templateVersionChange(previousTemplateVersionId, values.templateVersionId),
});

export const buildCreateBlueprintRequest = (
    values: BlueprintEditorValues,
    scope: BlueprintScope,
): CreateBlueprintRequest => ({
    name: values.name.trim(),
    summary: values.summary.trim() || undefined,
    description: values.description.trim() || undefined,
    configJson: JSON.stringify(values.config),
    exchangeDocuments: values.documents,
    generalTags: values.tags,
    scope,
    isActive: true,
    schemaDefinitionId: values.schemaDefinitionId,
    fieldDefaults: values.fieldDefaults.length > 0 ? values.fieldDefaults : undefined,
    informationRequestTemplateVersionId: values.templateVersionId,
});

export const libraryEntriesToDocuments = (entries: DocumentLibraryEntrySummaryDto[]): BlueprintDocumentConfig[] =>
    entries.map(entry => ({
        title: entry.title,
        libraryDocumentId: entry.id,
        restrictType: entry.restrictType ?? false,
        restrictedType: entry.restrictedType,
        required: entry.required ?? false,
    }));
