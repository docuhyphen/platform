import React from "react";
import {Text} from "@fluentui/react-components";
import {formatFileSize, getFileExtension} from "../exchangeDocumentUploadRules.ts";
import {useUploadFileSummaryStyles} from "./UploadFileSummaryStyles.tsx";

interface UploadFileSummaryProps
{
    file: File;
    targetDocumentTitle: string | undefined;
    restrictionLabel: string;
}

interface SummaryRow
{
    index: number;
    label: string;
    value: string | undefined;
}

const UploadFileSummary: React.FC<UploadFileSummaryProps> = (
    {
        file,
        targetDocumentTitle,
        restrictionLabel
    }) =>
{
    const styles = useUploadFileSummaryStyles();

    const rows: SummaryRow[] = [
        {index: 2, label: "File name", value: file.name},
        {index: 3, label: "Size", value: formatFileSize(file.size)},
        {index: 4, label: "Type", value: getFileExtension(file.name) || 'Unknown'},
        {index: 5, label: "Target document", value: targetDocumentTitle},
        {index: 6, label: "Restriction", value: restrictionLabel},
    ];

    return (
        <div id={"exchange-document-upload-dialog-div-1"}
             className={styles.fileInfoCard}>
            {rows.map((row) => (
                <div id={`exchange-document-upload-dialog-div-${row.index}`}
                     key={row.index}
                     className={styles.keyValueRow}>
                    <Text id={`exchange-document-upload-dialog-text-${row.index * 2 - 2}`}
                          className={styles.keyLabel}>{row.label}</Text>
                    <Text id={`exchange-document-upload-dialog-text-${row.index * 2 - 1}`}>{row.value}</Text>
                </div>
            ))}
        </div>
    );
};

export default UploadFileSummary;
