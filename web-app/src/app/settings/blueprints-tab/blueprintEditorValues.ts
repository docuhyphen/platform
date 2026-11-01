import {BlueprintConfig, BlueprintDefinitionDto, BlueprintDefinitionSummaryDto} from '../../models/models.tsx';
import {BlueprintEditorValues} from './blueprintEditorTypes.ts';

export const emptyBlueprintConfig = (): BlueprintConfig => ({
    requestRecipientSignIn: true,
    allowDocumentAddition: false,
    allowDocumentDeletion: false,
    allowDocumentDownload: false,
    allowDocumentUpdate: false,
    allowDocumentUpload: false,
});

export const parseBlueprintConfig = (configJson: string): BlueprintConfig =>
{
    try
    {
        return JSON.parse(configJson);
    }
    catch
    {
        return emptyBlueprintConfig();
    }
};

export const emptyBlueprintEditorValues = (): BlueprintEditorValues => ({
    name: '',
    summary: '',
    description: '',
    tags: [],
    config: emptyBlueprintConfig(),
    documents: [],
    schemaDefinitionId: undefined,
    fieldDefaults: [],
    templateVersionId: undefined,
});

export const blueprintEditorValuesFrom = (blueprint: BlueprintDefinitionSummaryDto): BlueprintEditorValues => ({
    name: blueprint.name,
    summary: blueprint.summary ?? '',
    description: (blueprint as BlueprintDefinitionDto).description ?? '',
    tags: blueprint.generalTags,
    config: parseBlueprintConfig(blueprint.configJson),
    documents: blueprint.exchangeDocuments ?? [],
    schemaDefinitionId: blueprint.schemaDefinitionId,
    fieldDefaults: blueprint.fieldDefaults ?? [],
    templateVersionId: blueprint.informationRequestTemplateVersionId,
});
