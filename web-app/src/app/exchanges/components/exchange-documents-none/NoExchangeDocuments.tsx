import {Button, Text} from "@fluentui/react-components";
import React from "react";
import {useNoExchangeDocumentsStyles} from "./NoExchangeDocumentsStyles.tsx";
import {DocumentAddIcon} from "../../../components/IconBundles.tsx";

interface NoDocumentsProps
{
    setIsDocumentAddDialogOpen: (isOpen: boolean) => void;
}

const NoExchangeDocuments: React.FC<NoDocumentsProps> = (
    {
        setIsDocumentAddDialogOpen
    }) =>
{
    const styles = useNoExchangeDocumentsStyles()

    return (
        <div id={"no-exchange-documents-div-1"}
             className={styles.noDocumentsContainer}>
            <Text id={"no-exchange-documents-text-1"}
                  size={500}>No documents available</Text>
            <Button
                id={"no-exchange-documents-add-btn"}
                onClick={() => setIsDocumentAddDialogOpen(true)}
                shape={"circular"}
                icon={<DocumentAddIcon/>}
                appearance={"primary"}>
                Add document
            </Button>
        </div>
    );
}

export default NoExchangeDocuments;