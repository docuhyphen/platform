import React from "react";
import {Checkbox, Field, Input, Text} from "@fluentui/react-components";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import {useExchangeDocumentZipSelectionStyles} from "./ExchangeDocumentZipSelectionStyles.tsx";

interface ExchangeDocumentZipSelectionProps
{
    exchange: ExchangeDetailedDto;
    downloadName: string;
    onDownloadNameChange: (downloadName: string) => void;
    onDocumentSelection: (documentId: string, isSelected: boolean) => void;
}

const ExchangeDocumentZipSelection: React.FC<ExchangeDocumentZipSelectionProps> = (
    {
        exchange,
        downloadName,
        onDownloadNameChange,
        onDocumentSelection
    }) =>
{
    const styles = useExchangeDocumentZipSelectionStyles();

    return (
        <div id={"exchange-document-zip-download-dialog-div-1"}
             className={styles.downloadContainer}>
            <Field id={"exchange-document-zip-download-dialog-field-1"}
                   className={styles.downloadNameField}>
                <Input id={"input-zip-download-name"}
                       type="text"
                       value={downloadName}
                       required
                       onChange={(e) => onDownloadNameChange(e.target.value)}
                       placeholder="Zip name"
                       contentAfter={<Text id={"exchange-document-zip-download-dialog-text-2"}
                                           size={400}>.zip</Text>}/>
            </Field>
            {exchange?.documents?.filter(d => d.uploadDate).map(document => (
                <Checkbox id={`checkbox-zip-download-doc-${document.id}`}
                          key={document.id}
                          label={document.title}
                          onChange={(_e, data) => onDocumentSelection(document.id, data.checked === true)}/>
            ))}
        </div>
    );
};

export default ExchangeDocumentZipSelection;
