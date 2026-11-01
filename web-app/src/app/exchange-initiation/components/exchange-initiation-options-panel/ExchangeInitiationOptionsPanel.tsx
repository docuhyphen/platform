import React from 'react';
import SharingOptionsTab from "../exchange-initiation-options-tab/ExchangeInitiationOptionsTab.tsx";
import {handleCheckboxChange} from "../../formHandlers.tsx";
import {ExchangeInitiationFormState} from "../../exchangeInitiationTypes.ts";

interface ExchangeInitiationOptionsPanelProps
{
    form: ExchangeInitiationFormState;
    locked: boolean;
}

const ExchangeInitiationOptionsPanel: React.FC<ExchangeInitiationOptionsPanelProps> = ({form, locked}) =>
{
    return (
        <SharingOptionsTab requireSignIn={form.requireSignIn}
                           allowDocumentAdditions={form.allowDocumentAdditions}
                           allowDocumentDeletions={form.allowDocumentDeletions}
                           allowDocumentDownload={form.allowDocumentDownload}
                           allowDocumentUpdate={form.allowDocumentUpdate}
                           allowDocumentUpload={form.allowDocumentUpload}
                           onRequireSignInChange={handleCheckboxChange(form.setRequireSignIn)}
                           onAllowDocumentAdditionsChange={handleCheckboxChange(form.setAllowDocumentAdditions)}
                           onAllowDocumentDeletionsChange={handleCheckboxChange(form.setAllowDocumentDeletions)}
                           onAllowDocumentDownloadChange={handleCheckboxChange(form.setAllowDocumentDownload)}
                           onAllowDocumentUpdateChange={handleCheckboxChange(form.setAllowDocumentUpdate)}
                           onAllowDocumentUploadChange={handleCheckboxChange(form.setAllowDocumentUpload)}
                           allowedDownloadFormats={form.allowedDownloadFormats}
                           onAllowedDownloadFormatsChange={form.setAllowedDownloadFormats}
                           locked={locked}/>
    );
};

export default ExchangeInitiationOptionsPanel;
