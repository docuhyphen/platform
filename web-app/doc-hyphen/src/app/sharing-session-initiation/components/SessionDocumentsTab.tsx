import React from 'react';
import {Button} from "@fluentui/react-components";
import {useSharingSessionInitiationStyles} from "../SharingSessionInitiationStyles.tsx";
import SessionDocumentsCard from "./SessionDocumentsCard.tsx";
import {SharingSessionRequestDocumentRequest} from "../../models/models.tsx";
import {DocumentAddIcon} from "../../components/IconBundles.tsx";

interface SessionDocumentsTabProps
{
    documents: SharingSessionRequestDocumentRequest[];
    onDocumentNameChange: (index: number, newValue: string) => void;
    onDocumentTypeChange: (index: number, newType: string) => void;
    onRestrictDocumentTypeChange: (index: number, ev: React.ChangeEvent<HTMLInputElement>) => void;
    onDeleteDocument: (index: number) => void;
    addNewDocument: () => void;
}

const SessionDocumentsTab: React.FC<SessionDocumentsTabProps> = (
    {
        documents,
        onDocumentNameChange,
        onDocumentTypeChange,
        onRestrictDocumentTypeChange,
        onDeleteDocument,
        addNewDocument
    }) =>
{
    const styles = useSharingSessionInitiationStyles();


    return (
        <div className={styles.sharingSessionDocumentsTabContent}>
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
            <div className={styles.addDocumentButtonContainer}>
                <Button onClick={addNewDocument}
                        shape={"circular"}
                        icon={<DocumentAddIcon/>}
                        appearance="subtle">
                    Add Document
                </Button>
            </div>
        </div>
    );
};

export default SessionDocumentsTab;