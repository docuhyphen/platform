import {BlueprintBooleanConfigKey} from '../blueprintEditorTypes.ts';

export interface BlueprintPermissionOption
{
    id: string;
    key: BlueprintBooleanConfigKey;
    label: string;
}

export const blueprintPermissionOptions: BlueprintPermissionOption[] = [
    {id: 'checkbox-request-recipient-sign-in', key: 'requestRecipientSignIn', label: 'Require recipient sign-in'},
    {id: 'checkbox-allow-document-addition', key: 'allowDocumentAddition', label: 'Allow document addition'},
    {id: 'checkbox-allow-document-deletion', key: 'allowDocumentDeletion', label: 'Allow document deletion'},
    {id: 'checkbox-allow-document-download', key: 'allowDocumentDownload', label: 'Allow document download'},
    {id: 'checkbox-allow-document-update', key: 'allowDocumentUpdate', label: 'Allow document update'},
    {id: 'checkbox-allow-document-upload', key: 'allowDocumentUpload', label: 'Allow document upload'},
];
