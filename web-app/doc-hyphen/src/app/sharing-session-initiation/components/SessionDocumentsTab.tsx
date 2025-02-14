import React from 'react';
import {Button, InputOnChangeData} from "@fluentui/react-components";
import { AddRegular } from "@fluentui/react-icons";
import {DocumentType, ImageType, SharingSessionRequestDocumentRequest} from "../../models/models.tsx";
import SessionDocumentsCard from "./SessionDocumentsCard.tsx";

interface SharingDocumentsTabProps {
    documents: SharingSessionRequestDocumentRequest[];
    onDocumentNameChange: (index: number, newValue: string) => void;
    onDocumentTypeChange: (index: number, newType: DocumentType | ImageType) => void;
    onRestrictDocumentTypeChange: (index: number, ev: React.ChangeEvent<HTMLInputElement>) => void;
    onDeleteDocument: (index: number) => void;
    addNewDocument: () => void;
}

const SharingDocumentsTab: React.FC<SharingDocumentsTabProps> = ({
    documents,
    onDocumentNameChange,
    onDocumentTypeChange,
    onRestrictDocumentTypeChange,
    onDeleteDocument,
    addNewDocument
}) => {
    return (
        <div id="sharing-session-documents-tab-content">
            {documents.map((document, index) => (
                <SessionDocumentsCard
                    key={index}
                    document={document}
                    index={index}
                    onDocumentNameChange={onDocumentNameChange}
                    onDocumentTypeChange={onDocumentTypeChange}
                    onRestrictDocumentTypeChange={onRestrictDocumentTypeChange}
                    onDeleteDocument={onDeleteDocument}
                />
            ))}
            <div>
                <Button onClick={addNewDocument} icon={<AddRegular />} appearance="subtle">
                    Add Document
                </Button>
            </div>
        </div>
    );
};

export default SharingDocumentsTab;