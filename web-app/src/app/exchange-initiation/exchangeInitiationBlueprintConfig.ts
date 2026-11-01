import {
    BlueprintConfig,
    BlueprintDefinitionSummaryDto,
    BlueprintDocumentConfig,
    ExchangeRequestDocumentRequest,
} from "../models/models.tsx";
import {ExchangeInitiationFormState} from "./exchangeInitiationTypes.ts";

export const parseBlueprintConfig = (blueprint: BlueprintDefinitionSummaryDto): BlueprintConfig | null =>
{
    try
    {
        return (JSON.parse(blueprint.configJson) as BlueprintConfig) ?? null;
    }
    catch
    {
        return null;
    }
};

export const isBlueprintLocked = (blueprint: BlueprintDefinitionSummaryDto, config: BlueprintConfig): boolean =>
    blueprint.scope !== 'PERSONAL' && !(config.allowEditOnExchangeStart === true);

export const applyBlueprintConfigToForm = (
    form: ExchangeInitiationFormState,
    blueprint: BlueprintDefinitionSummaryDto,
    config: BlueprintConfig,
) =>
{
    form.setExchangeName(config.name || blueprint.name);
    form.setDescription(config.description || blueprint.summary || '');
    if (config.initialShareMessage) form.setInitialShareMessage(config.initialShareMessage);
    if (config.requestRecipientSignIn !== undefined) form.setRequireSignIn(config.requestRecipientSignIn);
    if (config.allowDocumentAddition !== undefined) form.setAllowDocumentAdditions(config.allowDocumentAddition);
    if (config.allowDocumentDeletion !== undefined) form.setAllowDocumentDeletions(config.allowDocumentDeletion);
    if (config.allowDocumentDownload !== undefined) form.setAllowDocumentDownload(config.allowDocumentDownload);
    if (config.allowDocumentUpdate !== undefined) form.setAllowDocumentUpdate(config.allowDocumentUpdate);
    if (config.allowDocumentUpload !== undefined) form.setAllowDocumentUpload(config.allowDocumentUpload);
    if (config.allowedDownloadFormats !== undefined) form.setAllowedDownloadFormats(config.allowedDownloadFormats);
    if (blueprint.exchangeDocuments && blueprint.exchangeDocuments.length > 0)
    {
        const docs: ExchangeRequestDocumentRequest[] = blueprint.exchangeDocuments.map(d => ({
            title: d.title,
            restrictedType: d.restrictedType as ExchangeRequestDocumentRequest["restrictedType"],
            restrictType: d.restrictType ?? false,
            required: d.required ?? false,
            libraryDocumentId: d.libraryDocumentId,
        }));
        form.setDocuments(docs);
    }
};

export const buildBlueprintConfigJson = (form: ExchangeInitiationFormState): string =>
{
    const config: BlueprintConfig = {
        name: form.name,
        description: form.description,
        initialShareMessage: form.initialShareMessage,
        requestRecipientSignIn: form.requireSignIn,
        allowDocumentAddition: form.allowDocumentAdditions,
        allowDocumentDeletion: form.allowDocumentDeletions,
        allowDocumentDownload: form.allowDocumentDownload,
        allowDocumentUpdate: form.allowDocumentUpdate,
        allowDocumentUpload: form.allowDocumentUpload,
        allowedDownloadFormats: form.allowedDownloadFormats,
    };
    return JSON.stringify(config);
};

export const buildBlueprintDocuments = (documents: ExchangeRequestDocumentRequest[]): BlueprintDocumentConfig[] =>
    documents.map(d => ({
        title: d.title,
        restrictedType: d.restrictedType as string | undefined,
        restrictType: d.restrictType,
        required: d.required,
        libraryDocumentId: d.libraryDocumentId,
    }));
