import {Button, Text} from "@fluentui/react-components";
import React from "react";
import {useNoSessionDocumentsStyles} from "./NoSessionDocumentsStyles.tsx";
import {DocumentAddRegular} from "@fluentui/react-icons";

interface NoDocumentsProps
{
    setIsDocumentAddDialogOpen: (isOpen: boolean) => void;
}

const NoSessionDocuments: React.FC<NoDocumentsProps> = ({setIsDocumentAddDialogOpen}) =>
{
    const styles = useNoSessionDocumentsStyles()

    return (
        <div className={styles.noDocumentsContainer}>
            <Text size={500}>No documents available</Text>
            <Button onClick={() => setIsDocumentAddDialogOpen(true)}
                    icon={<DocumentAddRegular/>}
                    appearance={"primary"}>
                Add document
            </Button>
        </div>
    );
}

export default NoSessionDocuments;