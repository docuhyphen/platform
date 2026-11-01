import {BlueprintConfig, BlueprintDocumentConfig, BlueprintFieldDefaultConfig} from '../../models/models.tsx';

export type BlueprintEditorTab = 'details' | 'documents' | 'permissions' | 'fields' | 'informationRequest';

export type BlueprintBooleanConfigKey =
    | 'requestRecipientSignIn'
    | 'allowDocumentAddition'
    | 'allowDocumentDeletion'
    | 'allowDocumentDownload'
    | 'allowDocumentUpdate'
    | 'allowDocumentUpload'
    | 'allowEditOnExchangeStart';

export interface BlueprintEditorValues
{
    name: string;
    summary: string;
    description: string;
    tags: string[];
    config: BlueprintConfig;
    documents: BlueprintDocumentConfig[];
    schemaDefinitionId: string | undefined;
    fieldDefaults: BlueprintFieldDefaultConfig[];
    templateVersionId: string | undefined;
}
