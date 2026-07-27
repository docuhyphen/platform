import {Text} from '@fluentui/react-components';
import {CircleFilled, DocumentRegular} from '@fluentui/react-icons';
import {ExchangeDetailedDto} from '../../../models/models.tsx';
import {useExchangeAcceptanceDialogStyles} from './ExchangeAcceptanceDialogStyles.tsx';

interface ExchangeAcceptanceDocumentsSummaryProps
{
    exchange: ExchangeDetailedDto;
}

const ExchangeAcceptanceDocumentsSummary = ({exchange}: ExchangeAcceptanceDocumentsSummaryProps) =>
{
    const styles = useExchangeAcceptanceDialogStyles();
    const requestedDocuments = (exchange.documents || []).filter(document => !document.uploadDate);
    const sharedDocuments = (exchange.documents || []).filter(document => !!document.uploadDate);
    return (
        <div
            id={'exchange-acceptance-documents-section'}
            className={styles.section}
        >
            <div
                id={'exchange-acceptance-documents-label'}
                className={styles.sectionLabel}
            >
                <DocumentRegular
                    id={'exchange-acceptance-documents-icon'}
                    className={styles.sectionIcon}
                />
                <Text
                    id={'exchange-acceptance-documents-title'}
                    size={200}
                    weight={'semibold'}
                    className={styles.labelText}
                >
                    {requestedDocuments.length > 0 ? ` ${requestedDocuments.length}` : ''} Documents
                </Text>
            </div>
            <div
                id={'exchange-acceptance-documents-content'}
                className={styles.sectionContent}
            >
                {requestedDocuments.length > 0 ? (
                    <div
                        id={'exchange-acceptance-requested-document-list'}
                        className={styles.documentList}
                    >
                        {requestedDocuments.slice(0, 5).map(document => (
                            <div
                                id={`exchange-acceptance-requested-document-${document.id}`}
                                key={document.id}
                                className={styles.documentItem}
                            >
                                <CircleFilled
                                    id={`exchange-acceptance-requested-document-icon-${document.id}`}
                                    className={styles.documentIcon}
                                />
                                <Text
                                    id={`exchange-acceptance-requested-document-title-${document.id}`}
                                    size={200}
                                >
                                    {document.title}
                                </Text>
                            </div>
                        ))}
                    </div>
                ) : (
                    <Text
                        id={'exchange-acceptance-documents-empty-text'}
                        size={200}
                        className={styles.emptyText}
                    >
                        No document details provided yet.
                    </Text>
                )}
                {sharedDocuments.length > 0 && (
                    <>
                        <Text
                            id={'exchange-acceptance-shared-documents-label'}
                            size={200}
                            weight={'semibold'}
                            className={styles.alreadySharedLabel}
                        >
                            Already shared ({sharedDocuments.length})
                        </Text>
                        <div
                            id={'exchange-acceptance-shared-document-list'}
                            className={styles.documentList}
                        >
                            {sharedDocuments.slice(0, 5).map(document => (
                                <div
                                    id={`exchange-acceptance-shared-document-${document.id}`}
                                    key={document.id}
                                    className={styles.documentItem}
                                >
                                    <CircleFilled
                                        id={`exchange-acceptance-shared-document-icon-${document.id}`}
                                        className={styles.documentIcon}
                                    />
                                    <Text
                                        id={`exchange-acceptance-shared-document-title-${document.id}`}
                                        size={200}
                                    >
                                        {document.title}
                                    </Text>
                                </div>
                            ))}
                        </div>
                    </>
                )}
            </div>
        </div>
    );
};

export default ExchangeAcceptanceDocumentsSummary;
