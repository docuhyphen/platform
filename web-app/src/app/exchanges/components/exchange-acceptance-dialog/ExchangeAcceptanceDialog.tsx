import React from "react";
import {
    Button,
    Card,
    Checkbox,
    Field,
    Spinner,
    Text,
    Textarea
} from "@fluentui/react-components";
import {ExchangeDetailedDto, ExchangeStatus, UpdateExchangeRequest} from "../../../models/models.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {fetchSignedInUserAppUserExchange, updateExchange} from "../../../../services/exchangeApi.ts";
import {useExchangeAcceptanceDialogStyles} from "./ExchangeAcceptanceDialogStyles.tsx";
import {publishExchangeUpdate} from "../../../observable/exchangeObservables.ts";

interface ExchangeAcceptanceDialogProps
{
    exchange: ExchangeDetailedDto | null;
    // Controls whether "Decide Later" is shown; dialog remains blocking either way.
    isSingleExchange: boolean;
    canDecideLater: boolean;
    activeCount: number;
    archiveCount: number;
    onAccepted: (exchange: ExchangeDetailedDto) => void;
    onRejected: (exchange: ExchangeDetailedDto) => void;
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
    const [updatingExchange, setUpdatingExchange] = React.useState(false);
    const [rejectingExchange, setRejectingExchange] = React.useState(false);
    const [rejectReason, setRejectReason] = React.useState<string>('');
    const globalStyles = useGlobalStyles();
    const styles = useExchangeAcceptanceDialogStyles();

    if (!exchange) return null;

    const onAcceptOrReject = async (status: ExchangeStatus) =>
    {
        if (updatingExchange) return;
        setUpdatingExchange(true);
        try
        {
            const request: UpdateExchangeRequest = {status};
            if (rejectingExchange) request.rejectionReason = rejectReason;

            await updateExchange(exchange.id, request);
            const updatedExchange = await fetchSignedInUserAppUserExchange(exchange.id) as ExchangeDetailedDto;
            publishExchangeUpdate(updatedExchange);

            if (rejectingExchange)
            {
                setRejectReason('');
                onRejected(updatedExchange);
            }
            else
            {
                onAccepted(updatedExchange);
            }
        }
        catch (error)
        {
            alert("Error updating exchange");
            console.error("Error updating exchange", error);
        }
        finally
        {
            setUpdatingExchange(false);
        }
    };

    const cancelDecline = () =>
    {
        setRejectingExchange(false);
        setRejectReason('');
    };

    const initiatorName = [exchange.initiator?.person?.firstName, exchange.initiator?.person?.lastName]
        .filter(Boolean).join(' ') || exchange.initiator?.email;
    const initiatorEmail = exchange.initiator?.email || 'Not provided';
    const recipientEmail = exchange.recipient?.email || 'Not provided';

    const requestedDocuments = (exchange.documents || []).filter(document => !document.uploadDate);
    const sharedDocuments = (exchange.documents || []).filter(document => !!document.uploadDate);

    const cardInner = (
        <>
            <Text size={400} weight="semibold" align="center">New Document Request</Text>

            <Text size={300} align="center">
                <strong>{initiatorName} ({initiatorEmail})</strong> has requested to share documents with you.
            </Text>

            <div className={styles.nameContainer}>
                <Text size={200} weight="bold" align="center">SESSION</Text>
                <Text size={300} align="center">{exchange.name}</Text>
            </div>

            {exchange.initialShareMessage && (
                <div className={styles.messageContainer}>
                    <Text size={200} weight="bold" align="center">MESSAGE</Text>
                    <Text size={300} align="center">{exchange.initialShareMessage}</Text>
                </div>
            )}

            <div className={styles.documentsInfoContainer}>
                <Text size={200} weight="bold" align="center">DOCUMENTS</Text>

                {requestedDocuments.length > 0 && (
                    <div className={styles.documentsGroup}>
                        <Text size={200} weight="semibold" align="center">Requested from you ({requestedDocuments.length})</Text>
                        {requestedDocuments.slice(0, 5).map(document => (
                            <Text key={document.id} size={200} align="center">- {document.title}</Text>
                        ))}
                    </div>
                )}

                {sharedDocuments.length > 0 && (
                    <div className={styles.documentsGroup}>
                        <Text size={200} weight="semibold" align="center">Already shared ({sharedDocuments.length})</Text>
                        {sharedDocuments.slice(0, 5).map(document => (
                            <Text key={document.id} size={200} align="center">- {document.title}</Text>
                        ))}
                    </div>
                )}

                {requestedDocuments.length === 0 && sharedDocuments.length === 0 && (
                    <Text size={200} align="center">No document details provided yet.</Text>
                )}
            </div>

            {rejectingExchange && (
                <div className={styles.declineFieldContainer}>
                    <Field>
                        <Textarea
                            placeholder="Reason for declining (optional)"
                            value={rejectReason}
                            onChange={e => setRejectReason(e.target.value)}
                            maxLength={100}
                        />
                    </Field>
                    <Checkbox label="Report"/>
                </div>
            )}

            <div className={styles.actions}>
                <Button
                    appearance="primary"
                    disabled={updatingExchange}
                    className={globalStyles.buttonWithLoading}
                    shape="circular"
                    onClick={() => onAcceptOrReject(ExchangeStatus.ACCEPTED_STARTED)}>
                    {!rejectingExchange && updatingExchange && <Spinner size="tiny"/>}
                    Accept
                </Button>

                {!rejectingExchange && (
                    <Button
                        appearance="secondary"
                        shape="circular"
                        disabled={updatingExchange}
                        onClick={() => setRejectingExchange(true)}>
                        Decline
                    </Button>
                )}

                {rejectingExchange && (
                    <>
                        <Button
                            appearance="secondary"
                            className={globalStyles.buttonWithLoading}
                            shape="circular"
                            disabled={updatingExchange}
                            onClick={() => onAcceptOrReject(ExchangeStatus.REJECTED)}>
                            {updatingExchange && <Spinner size="tiny"/>}
                            Confirm Decline
                        </Button>
                        <Button
                            appearance="subtle"
                            shape="circular"
                            disabled={updatingExchange}
                            onClick={cancelDecline}>
                            Cancel
                        </Button>
                    </>
                )}

                {!isSingleExchange && !rejectingExchange && (
                    canDecideLater ?
                        <Button
                            appearance="subtle"
                            shape="circular"
                            disabled={updatingExchange}
                            onClick={onDismiss}>
                            Decide Later
                        </Button>
                        :
                        <>
                            <Button
                                appearance="secondary"
                                shape="circular"
                                disabled={updatingExchange || activeCount === 0}
                                onClick={onOpenActive}>
                                Open Active ({activeCount})
                            </Button>
                            <Button
                                appearance="secondary"
                                shape="circular"
                                disabled={updatingExchange || archiveCount === 0}
                                onClick={onOpenArchive}>
                                Open Archive ({archiveCount})
                            </Button>
                        </>
                )}
            </div>

            {!rejectingExchange && !canDecideLater && (
                <Text size={200} align="center" className={styles.navigationHint}>
                    This is the last pending inbox request.
                </Text>
            )}
        </>
    );


    return (
        <div className={styles.overlay}>
            <Card className={styles.overlayCard}>
                {cardInner}
            </Card>
        </div>
    );
};

export default ExchangeAcceptanceDialog;
