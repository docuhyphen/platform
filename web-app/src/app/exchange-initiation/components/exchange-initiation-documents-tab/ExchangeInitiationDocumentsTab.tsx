import React from 'react';
import {Button} from "@fluentui/react-components";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import ExchangeInitiationDocumentsCard from "../exchange-initiation-documents-card/ExchangeInitiationDocumentsCard.tsx";
import {AvailableVariablesDto, ExchangeRequestDocumentRequest} from "../../../models/models.tsx";
import {DocumentAddIcon} from "../../../components/IconBundles.tsx";

interface ExchangeDocumentsTabProps
{
    documents: ExchangeRequestDocumentRequest[];
    onDocumentNameChange: (index: number, newValue: string) => void;
    onDocumentTypeChange: (index: number, newType: string) => void;
    onRestrictDocumentTypeChange: (index: number, ev: React.ChangeEvent<HTMLInputElement>) => void;
    onDeleteDocument: (index: number) => void;
    onRequiredChange: (index: number, required: boolean) => void;
    addNewDocument: () => void;
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
        addNewDocument,
        availableVariables,
    }) =>
{
    const styles = useExchangeInitiationStyles();


    return (
        <div className={styles.exchangeDocumentsTabContent}>
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
                    availableVariables={availableVariables}
                />
            ))}
            <div className={styles.addDocumentButtonContainer}>
                <Button onClick={addNewDocument}
                        shape={"circular"}
                        icon={<DocumentAddIcon/>}
                        appearance="outline">
                    Add Document
                </Button>
            </div>
        </div>
    );
};

export default ExchangeInitiationDocumentsTab;