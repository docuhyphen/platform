import {
    AppUserDetailedDto,
    ExchangeInitiationRequest,
    ExchangeRequestDocumentRequest,
    SchemaAssignmentSource,
} from "../models/models.tsx";
import {ExchangeShareRoleName} from "../../services/types/roles.ts";
import {ExchangeInitiationFormState} from "./exchangeInitiationTypes.ts";
import {buildRecipientSelection} from "./exchangeInitiationRecipientSelection.ts";
import {buildCreationFieldValues} from "./components/exchange-initiation-fields-tab/creationFieldsUtils.ts";

export interface ExchangeInitiationRequestContext
{
    appUser: AppUserDetailedDto | null;
    variableOverrides: Record<string, string>;
    schemaFromBlueprint: boolean;
    blueprintDefinitionId?: string;
}

export const buildExchangeInitiationRequest = (
    form: ExchangeInitiationFormState,
    context: ExchangeInitiationRequestContext,
): ExchangeInitiationRequest =>
{
    const {appUser, variableOverrides, schemaFromBlueprint, blueprintDefinitionId} = context;
    const primaryRecipient = buildRecipientSelection({
        mode: form.recipientMode,
        organization: form.recipientOrg,
        appUser: form.recipientOrgUser,
        group: form.recipientOrgGroup,
        externalRecipient: form.newRecipient,
        resolutionId: form.recipientResolution?.id,
    });
    return {
        name: form.name,
        description: form.description,
        primaryRecipient,
        initialShareMessage: form.initialShareMessage,
        exchangeDocuments: form.documents.map((doc: ExchangeRequestDocumentRequest) => ({
            ...doc,
            restrictedType: doc.restrictType ? doc.restrictedType : undefined
        })),
        requestRecipientSignIn: form.requireSignIn,
        allowDocumentAddition: form.allowDocumentAdditions,
        allowDocumentDeletion: form.allowDocumentDeletions,
        allowDocumentDownload: form.allowDocumentDownload,
        allowDocumentUpdate: form.allowDocumentUpdate,
        allowDocumentUpload: form.allowDocumentUpload,
        allowedDownloadFormats: form.allowDocumentDownload ? (form.allowedDownloadFormats ?? undefined) : undefined,
        recipientRoleName: form.recipientRole,
        recipientConstraintsJson:
            Object.keys(form.recipientConstraints).length > 0
                ? JSON.stringify(form.recipientConstraints)
                : undefined,
        participants: form.internalParticipants
            ?.filter(p => !appUser || p.id !== appUser.id)
            ?.map((participant) => ({
                selection: {type: "REGISTERED_USER", appUserId: participant.id},
                role: ExchangeShareRoleName.PARTICIPANT,
            })),
        variableOverrides: Object.keys(variableOverrides).length > 0 ? variableOverrides : undefined,
        schemaDefinitionId: form.schemaDefinitionId || undefined,
        fieldValues: buildCreationFieldValues(form.schemaDefinitionId, form.fieldBindings, form.fieldValueMap),
        schemaAssignmentSource: schemaFromBlueprint && form.schemaDefinitionId
            ? SchemaAssignmentSource.BLUEPRINT
            : undefined,
        blueprintDefinitionId,
    };
};
