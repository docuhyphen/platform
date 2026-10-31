import React from "react";
import {MessageBar, MessageBarBody} from "@fluentui/react-components";
import {useInformationRequestDocumentsNoteStyles} from "./InformationRequestDocumentsNoteStyles.tsx";

const InformationRequestDocumentsNote: React.FC = () => {
    const styles = useInformationRequestDocumentsNoteStyles();

    return (
        <MessageBar id={"exchange-information-request-documents-note"}
                    className={styles.note}
                    intent={"info"}>
            <MessageBarBody id={"exchange-information-request-documents-note-text"}>
                {"This Blueprint's Information Request collects the documents, so Exchange documents are optional."}
            </MessageBarBody>
        </MessageBar>
    );
};

export default InformationRequestDocumentsNote;
