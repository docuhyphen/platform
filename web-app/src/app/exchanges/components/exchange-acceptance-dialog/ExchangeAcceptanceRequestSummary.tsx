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
        <div id={'exchange-acceptance-request-summary'} className={styles.container}>
            <div
                id={'exchange-acceptance-requester-section'}
                className={styles.section}
            >
                <div
                    id={'exchange-acceptance-requester-label'}
                    className={styles.sectionLabel}
                >
                    <PersonRegular
                        id={'exchange-acceptance-requester-icon'}
                        className={styles.sectionIcon}
                    />
                    <Text
                        id={'exchange-acceptance-requester-title'}
                        size={200}
                        weight={'semibold'}
                        className={styles.labelText}
                    >
                        Requester
                    </Text>
                </div>
                <div
                    id={'exchange-acceptance-requester-content'}
                    className={styles.sectionContent}
                >
                    <Persona
                        id={'exchange-acceptance-requester-persona'}
                        name={initiatorName}
                        secondaryText={initiatorEmail}
                        tertiaryText={exchange.initiator?.organization?.name}
                        size={'medium'}
                    />
                    <Text
                        id={'exchange-acceptance-requester-caption'}
                        size={200}
                        className={styles.requesterCaption}
                    >
                    </Text>
                </div>
            </div>
            <Divider id={'exchange-acceptance-requester-divider'}/>
            <div
                id={'exchange-acceptance-exchange-section'}
                className={styles.section}
            >
                <div
                    id={'exchange-acceptance-exchange-label'}
                    className={styles.sectionLabel}
                >
                    <FolderRegular
                        id={'exchange-acceptance-exchange-icon'}
                        className={styles.sectionIcon}
                    />
                    <Text
                        id={'exchange-acceptance-exchange-title'}
                        size={200}
                        weight={'semibold'}
                        className={styles.labelText}
                    >
                        Exchange
                    </Text>
                </div>
                <div
                    id={'exchange-acceptance-exchange-content'}
                    className={styles.sectionContent}
                >
                    <Text
                        id={'exchange-acceptance-exchange-name'}
                        size={300}
                    >
                        {exchange.name}
                    </Text>
                </div>
            </div>
            {exchange.initialShareMessage && (
                <>
                    <Divider id={'exchange-acceptance-message-divider'}/>
                    <div
                        id={'exchange-acceptance-message-section'}
                        className={styles.section}
                    >
                        <div
                            id={'exchange-acceptance-message-label'}
                            className={styles.sectionLabel}
                        >
                            <ChatRegular
                                id={'exchange-acceptance-message-icon'}
                                className={styles.sectionIcon}
                            />
                            <Text
                                id={'exchange-acceptance-message-title'}
                                size={200}
                                weight={'semibold'}
                                className={styles.labelText}
                            >
                                Message
                            </Text>
                        </div>
                        <div
                            id={'exchange-acceptance-message-content'}
                            className={styles.sectionContent}
                        >
                            <div
                                id={'exchange-acceptance-message-box'}
                                className={styles.messageBox}
                            >
                                <Text
                                    id={'exchange-acceptance-message-text'}
                                    size={200}
                                >
                                    {exchange.initialShareMessage}
                                </Text>
                            </div>
                        </div>
                    </div>
                </>
            )}
            <Divider id={'exchange-acceptance-documents-divider'}/>
            <ExchangeAcceptanceDocumentsSummary exchange={exchange}/>
        </div>
    );
};

export default ExchangeAcceptanceRequestSummary;
