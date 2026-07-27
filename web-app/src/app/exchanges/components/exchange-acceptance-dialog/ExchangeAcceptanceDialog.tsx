import React from 'react';
import {Card, Divider, Text} from '@fluentui/react-components';
import {ExchangeDetailedDto} from '../../../models/models.tsx';
import {useExchangeAcceptanceDialogStyles} from './ExchangeAcceptanceDialogStyles.tsx';
import ExchangeAcceptanceRequestSummary from './ExchangeAcceptanceRequestSummary.tsx';
import ExchangeAcceptanceNavigationNotice from './ExchangeAcceptanceNavigationNotice.tsx';
import ExchangeAcceptanceDecisionControls from './ExchangeAcceptanceDecisionControls.tsx';
import {useExchangeAcceptanceDecision} from './useExchangeAcceptanceDecision.ts';

interface ExchangeAcceptanceDialogProps
{
    exchange: ExchangeDetailedDto | null;
    isSingleExchange: boolean;
    canDecideLater: boolean;
    activeCount: number;
    archiveCount: number;
    onAccepted: (exchange: ExchangeDetailedDto) => void;
    onRejected: (exchangeId: string) => void;
    onDismiss: () => void;
    onOpenActive: () => void;
    onOpenArchive: () => void;
}

const ExchangeAcceptanceDialog: React.FC<ExchangeAcceptanceDialogProps> = (
    {
        exchange,
        isSingleExchange,
        canDecideLater,
        activeCount,
        archiveCount,
        onAccepted,
        onRejected,
        onDismiss,
        onOpenActive,
        onOpenArchive,
    }) =>
{
    const styles = useExchangeAcceptanceDialogStyles();
    const decision = useExchangeAcceptanceDecision(exchange, onAccepted, onRejected);

    if (!exchange)
    {
        return null;
    }

    return (
        <div
            id={'exchange-acceptance-overlay'}
            className={styles.overlay}
        >
            <Card
                id={'exchange-acceptance-card'}
                className={styles.overlayCard}
            >
                <Text
                    id={'exchange-acceptance-title'}
                    size={500}
                    weight={'semibold'}
                >
                    Exchange Request
                </Text>
                <Divider id={'exchange-acceptance-title-divider'}/>
                <ExchangeAcceptanceRequestSummary exchange={exchange}/>
                <ExchangeAcceptanceNavigationNotice
                    isSingleExchange={isSingleExchange}
                    canDecideLater={canDecideLater}
                    rejectingExchange={decision.rejectingExchange}
                    updatingExchange={decision.updatingExchange}
                    activeCount={activeCount}
                    archiveCount={archiveCount}
                    onOpenActive={onOpenActive}
                    onOpenArchive={onOpenArchive}
                />
                <ExchangeAcceptanceDecisionControls
                    isSingleExchange={isSingleExchange}
                    canDecideLater={canDecideLater}
                    updatingExchange={decision.updatingExchange}
                    rejectingExchange={decision.rejectingExchange}
                    rejectReason={decision.rejectReason}
                    dialogErrorMessage={decision.dialogErrorMessage}
                    onRejectReasonChange={decision.setRejectReason}
                    onBeginDecline={decision.beginDecline}
                    onCancelDecline={decision.cancelDecline}
                    onAccept={decision.accept}
                    onReject={decision.reject}
                    onDismiss={onDismiss}
                />
            </Card>
        </div>
    );
};

export default ExchangeAcceptanceDialog;
