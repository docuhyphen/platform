import React from "react";
import {Button, Caption1, Card, Text, Tooltip, mergeClasses} from "@fluentui/react-components";
import {ArrowUploadRegular, CheckmarkCircleFilled, CircleRegular} from "@fluentui/react-icons";
import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";
import ExchangeDocumentActionsMenu from "../exchange-document-actions-menu/ExchangeDocumentActionsMenu.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {useExchangeDocumentsListStyles} from "./ExchangeDocumentsListStyles.tsx";
import TruncatedDocumentTitle from "./TruncatedDocumentTitle.tsx";

interface ExchangeDocumentCardProps {
    document: DocumentDetailedDto;
    exchange: ExchangeDetailedDto | null;
    permissions: ExchangePermissions;
    selected: boolean;
    canUpload: boolean;
    onSelect: () => void;
    onUpload: () => void;
    onUpdate: () => void;
    onOpenDetails: () => void;
    onDelete: (documentId: string) => void;
}

const relativeUploadDate = (value: string) => {
    const uploaded = new Date(value);
    const today = new Date();
    uploaded.setHours(0, 0, 0, 0);
    today.setHours(0, 0, 0, 0);
    const days = Math.max(0, Math.round((today.getTime() - uploaded.getTime()) / 86400000));
    if (days === 0) return "Today";
    if (days === 1) return "Yesterday";
    return `${days} days ago`;
};

const ExchangeDocumentCard: React.FC<ExchangeDocumentCardProps> = (props) => {
    const styles = useExchangeDocumentsListStyles();
    const uploaded = !!props.document.uploadDate;
    const selectWithKeyboard = (event: React.KeyboardEvent<HTMLElement>) => {
        if (event.target !== event.currentTarget) return;
        if (event.key !== "Enter" && event.key !== " ") return;
        event.preventDefault();
        props.onSelect();
    };
    const stopAndUpload = (event: React.MouseEvent<HTMLButtonElement>) => {
        event.stopPropagation();
        props.onUpload();
    };

    return (
        <Card id={`exchange-document-card-${props.document.id}`}
              data-document-id={props.document.id}
              className={mergeClasses(styles.documentsCard, props.selected && styles.documentsCardSelected)}
              appearance="outline"
              role="button"
              tabIndex={0}
              aria-pressed={props.selected}
              aria-label={`Open ${props.document.title}`}
              onClick={props.onSelect}
              onKeyDown={selectWithKeyboard}>
            <TruncatedDocumentTitle documentId={props.document.id} title={props.document.title}/>
            <div id={`exchange-document-card-footer-${props.document.id}`} className={styles.cardFooter}>
                <div id={`exchange-document-status-${props.document.id}`} className={styles.statusGroup}>
                    {uploaded ? <CheckmarkCircleFilled className={styles.uploadedIcon}/> : <CircleRegular/>}
                    <Text size={200} weight="medium">{uploaded ? "Uploaded" : "Not Uploaded"}</Text>
                    {props.document.uploadDate && (
                        <Tooltip content={formatDateTimeWithOrdinal(props.document.uploadDate)}
                                 relationship="description">
                            <Caption1 className={styles.uploadDate}>· {relativeUploadDate(props.document.uploadDate)}</Caption1>
                        </Tooltip>
                    )}
                </div>
                <div id={`exchange-document-actions-${props.document.id}`} className={styles.cardActions}>
                    <Button id={`exchange-document-upload-${props.document.id}`}
                            appearance="subtle"
                            size="small"
                            shape="circular"
                            icon={<ArrowUploadRegular/>}
                            disabled={!props.canUpload}
                            onClick={stopAndUpload}>
                        {uploaded ? "Re-upload" : "Upload"}
                    </Button>
                    {props.exchange && (
                        <ExchangeDocumentActionsMenu exchange={props.exchange}
                                                     exchangeDocument={props.document}
                                                     permissions={props.permissions}
                                                     onUpload={props.onUpload}
                                                     onUpdate={props.onUpdate}
                                                     onOpenDetailsSidebar={props.onOpenDetails}
                                                     onDocumentDeleted={props.onDelete}
                                                     onPreviewDocument={props.onSelect}/>
                    )}
                </div>
            </div>
        </Card>
    );
};

export default ExchangeDocumentCard;
