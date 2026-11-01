import {RecreateRejectedExchangeDraft} from "../observable/exchangeObservables.ts";
import {ExchangeInitiationFormState} from "./exchangeInitiationTypes.ts";
import {
    ExchangeInitiationRecipientMode
} from "./components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts";

export const applyRecreatedExchangeDraft = (form: ExchangeInitiationFormState, draft: RecreateRejectedExchangeDraft) =>
{
    form.setChoosingBlueprint(false);
    form.setMessageGroupMessages([]);
    form.setExchangeInitiatedSuccessfully(false);
    form.setInitiatingExchange(false);

    form.setExchangeName(draft.name || '');
    form.setDescription(draft.description || '');
    form.setInitialShareMessage(draft.initialShareMessage || '');
    form.setRequireSignIn(!!draft.requestRecipientSignIn);
    form.setAllowDocumentAdditions(!!draft.allowDocumentAddition);
    form.setAllowDocumentDeletions(!!draft.allowDocumentDeletion);
    form.setAllowDocumentDownload(!!draft.allowDocumentDownload);
    form.setAllowDocumentUpdate(!!draft.allowDocumentUpdate);
    form.setAllowDocumentUpload(!!draft.allowDocumentUpload);
    form.setAllowedDownloadFormats(draft.allowedDownloadFormats);
    form.setDocuments(draft.exchangeDocuments || []);
    form.setSelectedTab('details-tab');
    form.setRecipientOrg(undefined);
    form.setRecipientOrgGroup(undefined);
    form.setRecipientMode(ExchangeInitiationRecipientMode.PEOPLE);

    if (draft.recipientUser)
    {
        form.setRecipientOrgUser(draft.recipientUser);
        form.setNewRecipient({
            email: '',
            firstName: '',
            lastName: '',
        });
        return;
    }
    form.setRecipientOrgUser(undefined);
    form.setNewRecipient({
        email: draft.recipientEmail || '',
        firstName: draft.recipientFirstName || '',
        lastName: draft.recipientLastName || '',
    });
};
