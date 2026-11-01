import {ExchangeInitiationFormState} from "./exchangeInitiationTypes.ts";
import {
    ExchangeInitiationRecipientMode
} from "./components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts";

export const applyRequestingDocumentsMode = (form: ExchangeInitiationFormState, isRequesting: boolean) =>
{
    form.setRequestingDocuments(isRequesting);
    if (isRequesting)
    {
        form.setAllowDocumentUpload(true);
        form.setAllowDocumentAdditions(true);
        form.setAllowDocumentDownload(false);
        form.setAllowDocumentUpdate(false);
        form.setAllowDocumentDeletions(false);
    }
    else
    {
        form.setAllowDocumentDownload(true);
        form.setAllowDocumentUpload(false);
        form.setAllowDocumentAdditions(false);
        form.setAllowDocumentUpdate(false);
        form.setAllowDocumentDeletions(false);
    }
};

export const resetExchangeInitiationFields = (form: ExchangeInitiationFormState) =>
{
    form.setChoosingBlueprint(false);
    form.setMessageGroupMessages([]);
    form.setInitiatingExchange(false);
    form.setExchangeInitiatedSuccessfully(false);
    form.setRecipientOrg(null);
    form.setRecipientOrgUser(null);
    form.setRecipientOrgGroup(null);
    form.setRecipientMode(ExchangeInitiationRecipientMode.PEOPLE);
    form.setNewRecipient({
        email: '',
        firstName: '',
        lastName: '',
    });
    form.setExchangeName('');
    form.setDescription('');
    form.setInitialShareMessage('');
    form.setRequireSignIn(true);
    form.setAllowDocumentAdditions(false);
    form.setAllowDocumentDeletions(false);
    form.setAllowDocumentDownload(false);
    form.setAllowDocumentUpdate(false);
    form.setAllowDocumentUpload(false);
    form.setAllowedDownloadFormats(undefined);
    form.setRequestingDocuments(true);
    form.setDocuments([]);
    form.setSelectedTab('recipients-tab');
    form.setRecipientRole(undefined);
    form.setRecipientConstraints({});
};
