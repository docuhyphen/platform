import {useEffect, useRef, useState} from "react";
import {
    Button,
    Caption1,
    mergeClasses,
    Table,
    TableBody,
    TableCell,
    TableRow,
    Text,
} from "@fluentui/react-components";
import {ChevronDownRegular, ChevronRightRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto} from "../../../../models/models.tsx";
import {documentMetadataRows} from "./documentMetadata.ts";
import {useExchangeDocumentMetadataStyles} from "./ExchangeDocumentMetadataStyles.tsx";

interface ExchangeDocumentMetadataProps
{
    document: DocumentDetailedDto;
}

const ExchangeDocumentMetadata = ({document}: ExchangeDocumentMetadataProps) =>
{
    const styles = useExchangeDocumentMetadataStyles();
    const [showMetadata, setShowMetadata] = useState(false);
    const [isClosing, setIsClosing] = useState(false);
    const closeTimeoutRef = useRef<number | null>(null);

    const clearCloseTimeout = () =>
    {
        if (closeTimeoutRef.current) window.clearTimeout(closeTimeoutRef.current);
    };

    useEffect(() => () => clearCloseTimeout(), []);
    useEffect(() =>
    {
        clearCloseTimeout();
        setShowMetadata(false);
        setIsClosing(false);
    }, [document.id]);

    const toggleMetadata = () =>
    {
        clearCloseTimeout();
        if (!showMetadata)
        {
            setIsClosing(false);
            setShowMetadata(true);
            return;
        }
        setIsClosing(true);
        closeTimeoutRef.current = window.setTimeout(() =>
        {
            setShowMetadata(false);
            setIsClosing(false);
        }, 180);
    };
    const expanded = showMetadata && !isClosing;

    return (
        <div id={"exchange-document-sidebar-metadata"}>
            <div
                id={"exchange-document-sidebar-title-row"}
                className={styles.titleRow}
            >
                <Button
                    id={"exchange-document-sidebar-metadata-toggle"}
                    className={styles.toggleButton}
                    appearance={"subtle"}
                    size={"small"}
                    shape={"circular"}
                    aria-label={expanded ? "Hide document details" : "Show document details"}
                    icon={expanded ? <ChevronDownRegular/> : <ChevronRightRegular/>}
                    onClick={toggleMetadata}
                />
                <Text
                    id={"exchange-document-sidebar-title"}
                    className={styles.title}
                >{document.title}</Text>
            </div>
            {(showMetadata || isClosing) && (
                <section
                    id={"exchange-document-sidebar-metadata-panel"}
                    className={mergeClasses(styles.panel, expanded ? styles.panelEntering : styles.panelLeaving)}
                >
                    <Table
                        id={"exchange-document-sidebar-metadata-table"}
                        noNativeElements
                        size={"small"}
                        className={styles.table}
                    >
                        <TableBody id={"exchange-document-sidebar-metadata-body"}>
                            {documentMetadataRows(document).map((row, index) => (
                                <TableRow
                                    id={`exchange-document-sidebar-metadata-row-${index}`}
                                    className={styles.row}
                                    key={row.label}
                                >
                                    <TableCell
                                        id={`exchange-document-sidebar-metadata-label-cell-${index}`}
                                        className={styles.labelCell}
                                    ><Caption1 className={styles.label}>{row.label}</Caption1></TableCell>
                                    <TableCell
                                        id={`exchange-document-sidebar-metadata-value-cell-${index}`}
                                        className={styles.valueCell}
                                    ><Caption1 className={styles.value}>{row.value}</Caption1></TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </section>
            )}
        </div>
    );
};

export default ExchangeDocumentMetadata;
