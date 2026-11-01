import React from 'react';
import SharingDocumentsTab from "../exchange-initiation-documents-tab/ExchangeInitiationDocumentsTab.tsx";
import {handleDocumentChange} from "../../formHandlers.tsx";
import {
    AvailableVariablesDto,
    DocumentLibraryEntrySummaryDto,
    DocumentType,
    ExchangeRequestDocumentRequest,
} from "../../../models/models.tsx";
import {ExchangeInitiationFormState} from "../../exchangeInitiationTypes.ts";

interface ExchangeInitiationDocumentsPanelProps
{
    form: ExchangeInitiationFormState;
    availableVariables: AvailableVariablesDto | null;
    locked: boolean;
    canUseDocumentLibrary: boolean;
    startsInformationRequest: boolean;
}

type DocumentField = keyof ExchangeRequestDocumentRequest;
type DocumentFieldValue = ExchangeRequestDocumentRequest[DocumentField];

const ExchangeInitiationDocumentsPanel: React.FC<ExchangeInitiationDocumentsPanelProps> = (
    {
        form,
        availableVariables,
        locked,
        canUseDocumentLibrary,
        startsInformationRequest,
    }) =>
{
    const {documents, setDocuments, setMessageGroupMessages} = form;

    const changeDocument = (index: number, key: DocumentField, value: DocumentFieldValue) =>
    {
        setMessageGroupMessages([]);
        handleDocumentChange(documents, setDocuments)(index, key, value);
    };

    const addNewDocument = () =>
    {
        setMessageGroupMessages([]);
        setDocuments([...documents, {
            title: '',
            restrictedType: DocumentType.PDF,
            restrictType: false
        }]);
    };

    const addLibraryDocument = (entry: DocumentLibraryEntrySummaryDto) =>
    {
        setMessageGroupMessages([]);
        setDocuments(prev => [...prev, {
            title: entry.title,
            libraryDocumentId: entry.id,
            restrictType: entry.restrictType ?? false,
            restrictedType: entry.restrictedType,
            required: entry.required ?? false,
        } as ExchangeRequestDocumentRequest]);
    };

    const deleteDocument = (index: number) =>
    {
        setMessageGroupMessages([]);
        setDocuments(prevDocuments => prevDocuments.filter((_, i) => i !== index));
    };

    return (
        <SharingDocumentsTab documents={documents}
                             onDocumentNameChange={(index, value) => changeDocument(index, 'title', value)}
                             onDocumentTypeChange={(index, value) => changeDocument(index, 'restrictedType', value)}
                             onRestrictDocumentTypeChange={(index, ev) =>
                                 changeDocument(index, 'restrictType', ev.target.checked)}
                             onRequiredChange={(index, value) => changeDocument(index, 'required', value)}
                             onDeleteDocument={deleteDocument}
                             onUnlink={(index) => changeDocument(index, 'libraryDocumentId', undefined)}
                             addNewDocument={addNewDocument}
                             addLibraryDocument={addLibraryDocument}
                             availableVariables={availableVariables ?? undefined}
                             locked={locked}
                             canUseDocumentLibrary={canUseDocumentLibrary}
                             informationRequestCollectsDocuments={startsInformationRequest}/>
    );
};

export default ExchangeInitiationDocumentsPanel;
