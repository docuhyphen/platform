import React, {useState} from 'react';
import {
    Button,
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
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
    }) =>
{
    const styles = useExchangeInitiationStyles();
    const [pickerOpen, setPickerOpen] = useState(false);

    const handleLibrarySelect = (entry: DocumentLibraryEntrySummaryDto) =>
    {
        addLibraryDocument(entry);
        setPickerOpen(false);
    };

    return (
        <>
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
                    />
                ))}
                <div
                    id="exchange-documents-actions"
                    className={styles.addDocumentButtonContainer}
                >
                    <Button
                        id="exchange-add-document-btn"
                        onClick={addNewDocument}
                        shape="circular"
                        icon={<DocumentAddIcon/>}
                        appearance="outline"
                    >
                        Add Document
                    </Button>
                    <Button
                        id="exchange-pick-from-library-btn"
                        onClick={() => setPickerOpen(true)}
                        shape="circular"
                        icon={<PickFromLibraryIcon/>}
                        appearance="outline"
                    >
                        Pick from Library
                    </Button>
                </div>
            </div>

            <Dialog
                open={pickerOpen}
                onOpenChange={(_, d) => { if (!d.open) setPickerOpen(false); }}
            >
                <DialogSurface style={{maxWidth: '560px', width: '100%'}}>
                    <DialogBody>
                        <DialogTitle>Pick from Document Library</DialogTitle>
                        <DialogContent>
                            <DocumentLibraryPicker
                                onSelect={handleLibrarySelect}
                                onCancel={() => setPickerOpen(false)}
                            />
                        </DialogContent>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </>
    );
};

export default ExchangeInitiationDocumentsTab;