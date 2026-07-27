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
            <div className={styles.sectionLabel}>
                <DocumentRegular className={styles.sectionIcon}/>
                <Text
                    size={200}
                    weight={'semibold'}
                    className={styles.labelText}
                >
                    {requestedDocuments.length > 0 ? ` ${requestedDocuments.length}` : ''} Documents
                </Text>
            </div>
            <div className={styles.sectionContent}>
                {requestedDocuments.length > 0 ? (
                    <div className={styles.documentList}>
                        {requestedDocuments.slice(0, 5).map(document => (
                            <div
                                key={document.id}
                                className={styles.documentItem}
                            >
                                <CircleFilled className={styles.documentIcon}/>
                                <Text size={200}>{document.title}</Text>
                            </div>
                        ))}
                    </div>
                ) : (
                    <Text
                        size={200}
                        className={styles.emptyText}
                    >
                        No document details provided yet.
                    </Text>
                )}
                {sharedDocuments.length > 0 && (
                    <>
                        <Text
                            size={200}
                            weight={'semibold'}
                            className={styles.alreadySharedLabel}
                        >
                            Already shared ({sharedDocuments.length})
                        </Text>
                        <div className={styles.documentList}>
                            {sharedDocuments.slice(0, 5).map(document => (
                                <div
                                    key={document.id}
                                    className={styles.documentItem}
                                >
                                    <CircleFilled className={styles.documentIcon}/>
                                    <Text size={200}>{document.title}</Text>
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
