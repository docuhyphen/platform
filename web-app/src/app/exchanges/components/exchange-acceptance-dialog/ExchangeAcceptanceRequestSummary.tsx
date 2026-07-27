import {Divider, Persona, Text} from '@fluentui/react-components';
import {
    ChatRegular,
    FolderRegular,
    PersonRegular,
} from '@fluentui/react-icons';
import {ExchangeDetailedDto} from '../../../models/models.tsx';
import {useExchangeAcceptanceDialogStyles} from './ExchangeAcceptanceDialogStyles.tsx';
import ExchangeAcceptanceDocumentsSummary from './ExchangeAcceptanceDocumentsSummary.tsx';

interface ExchangeAcceptanceRequestSummaryProps
{
    exchange: ExchangeDetailedDto;
}

const ExchangeAcceptanceRequestSummary = ({exchange}: ExchangeAcceptanceRequestSummaryProps) =>
{
    const styles = useExchangeAcceptanceDialogStyles();
    const initiatorName = [exchange.initiator?.person?.firstName, exchange.initiator?.person?.lastName]
        .filter(Boolean).join(' ') || exchange.initiator?.email || '';
    const initiatorEmail = exchange.initiator?.email || 'Not provided';

    return (
        <div id={'exchange-acceptance-request-summary'}>
            <div
                id={'exchange-acceptance-requester-section'}
                className={styles.section}
            >
                <div className={styles.sectionLabel}>
                    <PersonRegular className={styles.sectionIcon}/>
                    <Text
                        size={200}
                        weight={'semibold'}
                        className={styles.labelText}
                    >
                        Requester
                    </Text>
                </div>
                <div className={styles.sectionContent}>
                    <Persona
                        name={initiatorName}
                        secondaryText={initiatorEmail}
                        tertiaryText={exchange.initiator?.organization?.name}
                        size={'medium'}
                    />
                    <Text
                        size={200}
                        className={styles.requesterCaption}
                    >
                        wants to exchange documents with you.
                    </Text>
                </div>
            </div>
            <Divider/>
            <div
                id={'exchange-acceptance-exchange-section'}
                className={styles.section}
            >
                <div className={styles.sectionLabel}>
                    <FolderRegular className={styles.sectionIcon}/>
                    <Text
                        size={200}
                        weight={'semibold'}
                        className={styles.labelText}
                    >
                        Exchange
                    </Text>
                </div>
                <div className={styles.sectionContent}>
                    <Text size={300}>{exchange.name}</Text>
                </div>
            </div>
            {exchange.initialShareMessage && (
                <>
                    <Divider/>
                    <div
                        id={'exchange-acceptance-message-section'}
                        className={styles.section}
                    >
                        <div className={styles.sectionLabel}>
                            <ChatRegular className={styles.sectionIcon}/>
                            <Text
                                size={200}
                                weight={'semibold'}
                                className={styles.labelText}
                            >
                                Message
                            </Text>
                        </div>
                        <div className={styles.sectionContent}>
                            <div className={styles.messageBox}>
                                <Text size={200}>{exchange.initialShareMessage}</Text>
                            </div>
                        </div>
                    </div>
                </>
            )}
            <Divider/>
            <ExchangeAcceptanceDocumentsSummary exchange={exchange}/>
        </div>
    );
};

export default ExchangeAcceptanceRequestSummary;
