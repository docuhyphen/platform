import React, {useState} from 'react';
import {Button} from "@fluentui/react-components";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import ExchangeInitiationDocumentsCard from "../exchange-initiation-documents-card/ExchangeInitiationDocumentsCard.tsx";
import {AvailableVariablesDto, DocumentLibraryEntrySummaryDto, ExchangeRequestDocumentRequest} from "../../../models/models.tsx";
import {DocumentAddIcon, PickFromLibraryIcon} from "../../../components/IconBundles.tsx";
import DocumentLibraryPicker from "../document-library-picker/DocumentLibraryPicker.tsx";

interface ExchangeDocumentsTabProps
{
    documents: ExchangeRequestDocumentRequest[];
    onDocumentNameChange: (index: number, newValue: string) => void;
    onDocumentTypeChange: (index: number, newType: string) => void;
    onRestrictDocumentTypeChange: (index: number, ev: React.ChangeEvent<HTMLInputElement>) => void;
    onDeleteDocument: (index: number) => void;
    onRequiredChange: (index: number, required: boolean) => void;
    onUnlink: (index: number) => void;
    addNewDocument: () => void;
    addLibraryDocument: (entry: DocumentLibraryEntrySummaryDto) => void;
    availableVariables?: AvailableVariablesDto;
    locked?: boolean;
    canUseDocumentLibrary: boolean;
}

const ExchangeInitiationDocumentsTab: React.FC<ExchangeDocumentsTabProps> = (
    {
        documents,
        onDocumentNameChange,
        onDocumentTypeChange,
        onRestrictDocumentTypeChange,
        onDeleteDocument,
        onRequiredChange,
        onUnlink,
        addNewDocument,
        addLibraryDocument,
        availableVariables,
        locked,
        canUseDocumentLibrary,
    }) =>
{
    const styles = useExchangeInitiationStyles();
    const [pickerOpen, setPickerOpen] = useState(false);

    const handleLibrarySelect = (entries: DocumentLibraryEntrySummaryDto[]) =>
    {
        entries.forEach(entry => addLibraryDocument(entry));
        setPickerOpen(false);
    };

    if (pickerOpen)
    {
        return (
            <DocumentLibraryPicker
                onSelect={handleLibrarySelect}
                onBack={() => setPickerOpen(false)}
            />
        );
    }

    return (
        <div
            id="exchange-documents-tab"
            className={styles.exchangeDocumentsTabContent}
        >
            {documents.map((document, index) => (
                <ExchangeInitiationDocumentsCard
                    key={index}
                    document={document}
                    index={index}
                    onDocumentNameChange={onDocumentNameChange}
                    onDocumentTypeChange={onDocumentTypeChange}
                    onRestrictDocumentTypeChange={onRestrictDocumentTypeChange}
                    onDeleteDocument={onDeleteDocument}
                    onRequiredChange={onRequiredChange}
                    onUnlink={onUnlink}
                    availableVariables={availableVariables}
                    locked={locked}
                />
            ))}
            {!locked && (
                <div
                    id="exchange-documents-actions"
                    className={styles.addDocumentButtonContainer}
                >
                    <Button
                        id="exchange-add-document-btn"
                        onClick={addNewDocument}
                        shape="circular"
                        icon={<DocumentAddIcon/>}
                        appearance="subtle"
                    >
                        Add Document
                    </Button>
                    {canUseDocumentLibrary && (
                        <Button
                            id="exchange-pick-from-library-btn"
                            onClick={() => setPickerOpen(true)}
                            shape="circular"
                            icon={<PickFromLibraryIcon/>}
                            appearance="subtle"
                        >
                            Pick from Library
                        </Button>
                    )}
                </div>
            )}
        </div>
    );
};

export default ExchangeInitiationDocumentsTab;
