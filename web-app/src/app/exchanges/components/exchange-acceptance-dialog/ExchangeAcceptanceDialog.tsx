import React, {useState} from "react";
import {
    Button,
    Card,
    Checkbox,
    Divider,
    Field,
    MessageBar,
    MessageBarBody,
    Persona,
    Spinner,
    Text,
    Textarea
} from "@fluentui/react-components";
import {
    ChatRegular,
    CheckmarkCircleRegular, CircleFilled,
    DismissCircleRegular, Document16Filled, Document24Filled, Document28Filled, Document32Filled, DocumentFilled,
    DocumentRegular,
    FolderRegular,
    InfoRegular,
    PersonRegular
} from "@fluentui/react-icons";
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
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);
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
            setDialogErrorMessage("Error updating exchange");
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
        .filter(Boolean).join(' ') || exchange.initiator?.email || '';
    const initiatorEmail = exchange.initiator?.email || 'Not provided';
    const initiatorOrg = exchange.initiator?.organization?.name;

    const requestedDocuments = (exchange.documents || []).filter(document => !document.uploadDate);
    const sharedDocuments = (exchange.documents || []).filter(document => !!document.uploadDate);

    return (
        <div className={styles.overlay}>
            <Card className={styles.overlayCard}>

                <Text size={500} weight="semibold">Exchange Request</Text>

                <Divider/>

                {/* Requester */}
                <div className={styles.section}>
                    <div className={styles.sectionLabel}>
                        <PersonRegular className={styles.sectionIcon}/>
                        <Text size={200} weight="semibold" className={styles.labelText}>Requester</Text>
                    </div>
                    <div className={styles.sectionContent}>
                        <Persona
                            name={initiatorName}
                            secondaryText={initiatorEmail}
                            tertiaryText={initiatorOrg}
                            size="medium"
                        />
                        <Text size={200} className={styles.requesterCaption}>wants to exchange documents with you.</Text>
                    </div>
                </div>

                <Divider/>

                {/* Exchange */}
                <div className={styles.section}>
                    <div className={styles.sectionLabel}>
                        <FolderRegular className={styles.sectionIcon}/>
                        <Text size={200} weight="semibold" className={styles.labelText}>Exchange</Text>
                    </div>
                    <div className={styles.sectionContent}>
                        <Text size={300}>{exchange.name}</Text>
                    </div>
                </div>

                {/* Message */}
                {exchange.initialShareMessage && (
                    <>
                        <Divider/>
                        <div className={styles.section}>
                            <div className={styles.sectionLabel}>
                                <ChatRegular className={styles.sectionIcon}/>
                                <Text size={200} weight="semibold" className={styles.labelText}>Message</Text>
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

                <div className={styles.section}>
                    <div className={styles.sectionLabel}>
                        <DocumentRegular className={styles.sectionIcon}/>
                        <Text size={200} weight="semibold" className={styles.labelText}>
                            {requestedDocuments.length > 0 ? ` ${requestedDocuments.length}` : ''} Documents
                        </Text>
                    </div>
                    <div className={styles.sectionContent}>
                        {requestedDocuments.length > 0 ? (
                            <div className={styles.documentList}>
                                {requestedDocuments.slice(0, 5).map(document => (
                                    <div key={document.id} className={styles.documentItem}>
                                        <CircleFilled className={styles.documentIcon}/>
                                        <Text size={200}>{document.title}</Text>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <Text size={200} className={styles.emptyText}>No document details provided yet.</Text>
                        )}

                        {sharedDocuments.length > 0 && (
                            <>
                                <Text size={200} weight="semibold" className={styles.alreadySharedLabel}>
                                    Already shared ({sharedDocuments.length})
                                </Text>
                                <div className={styles.documentList}>
                                    {sharedDocuments.slice(0, 5).map(document => (
                                        <div key={document.id} className={styles.documentItem}>
                                            <CircleFilled className={styles.documentIcon}/>
                                            <Text size={200}>{document.title}</Text>
                                        </div>
                                    ))}
                                </div>
                            </>
                        )}
                    </div>
                </div>

                {/* InfoBar: last pending request */}
                {!rejectingExchange && !canDecideLater && (
                    <MessageBar intent="info" icon={<InfoRegular/>}>
                        <MessageBarBody>
                            <Text size={200}>This is your last pending request.</Text>
                        </MessageBarBody>
                    </MessageBar>
                )}

                {/* Decline reason */}
                {rejectingExchange && (
                    <div className={styles.declineFieldContainer}>
                        <Field>
                            <Textarea
                                id={"textarea-acceptance-reject-reason"}
                                placeholder="Reason for declining (optional)"
                                value={rejectReason}
                                onChange={e => setRejectReason(e.target.value)}
                                maxLength={100}
                            />
                        </Field>
                        <Checkbox id={"checkbox-acceptance-report"} label="Report"/>
                    </div>
                )}

                {dialogErrorMessage && (
                    <MessageBar intent="error">
                        <MessageBarBody>
                            <Text size={200}>{dialogErrorMessage}</Text>
                        </MessageBarBody>
                    </MessageBar>
                )}

                <Divider/>

                {/* Actions */}
                <div className={styles.actions}>
                    <div className={styles.primaryActions}>
                        <Button
                            id={"acceptance-accept-btn"}
                            appearance="primary"
                            disabled={updatingExchange}
                            className={globalStyles.buttonWithLoading}
                            shape="circular"
                            onClick={() => onAcceptOrReject(ExchangeStatus.ACCEPTED_STARTED)}>
                            {(!rejectingExchange && updatingExchange) && <Spinner size="tiny"/>}
                            Accept
                        </Button>

                        {!rejectingExchange && (
                            <Button
                                id={"acceptance-decline-btn"}
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
                                    id={"acceptance-confirm-decline-btn"}
                                    appearance="secondary"
                                    className={globalStyles.buttonWithLoading}
                                    shape="circular"
                                    disabled={updatingExchange}
                                    onClick={() => onAcceptOrReject(ExchangeStatus.REJECTED)}>
                                    {updatingExchange && <Spinner size="tiny"/>}
                                    Confirm Decline
                                </Button>
                                <Button
                                    id={"acceptance-cancel-decline-btn"}
                                    appearance="subtle"
                                    shape="circular"
                                    disabled={updatingExchange}
                                    onClick={cancelDecline}>
                                    Cancel
                                </Button>
                            </>
                        )}
                    </div>

                    {!isSingleExchange && !rejectingExchange && (
                        <div className={styles.secondaryActions}>
                            {canDecideLater ? (
                                <Button
                                    id={"acceptance-decide-later-btn"}
                                    appearance="subtle"
                                    shape="circular"
                                    disabled={updatingExchange}
                                    onClick={onDismiss}>
                                    Decide Later
                                </Button>
                            ) : (
                                <>
                                    <Button
                                        id={"acceptance-open-active-btn"}
                                        appearance="secondary"
                                        shape="circular"
                                        disabled={updatingExchange || activeCount === 0}
                                        onClick={onOpenActive}>
                                        Open Active ({activeCount})
                                    </Button>
                                    <Button
                                        id={"acceptance-open-archive-btn"}
                                        appearance="secondary"
                                        shape="circular"
                                        disabled={updatingExchange || archiveCount === 0}
                                        onClick={onOpenArchive}>
                                        Open Archive ({archiveCount})
                                    </Button>
                                </>
                            )}
                        </div>
                    )}
                </div>

            </Card>
        </div>
    );
};

export default ExchangeAcceptanceDialog;
