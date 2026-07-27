import {Button, Checkbox, Divider, Field, MessageBar, MessageBarBody, Spinner, Text, Textarea}
    from '@fluentui/react-components';
import {useGlobalStyles} from '../../../../GlobalStyles.tsx';
import {useExchangeAcceptanceDialogStyles} from './ExchangeAcceptanceDialogStyles.tsx';

interface ExchangeAcceptanceDecisionControlsProps
{
    isSingleExchange: boolean;
    canDecideLater: boolean;
    updatingExchange: boolean;
    rejectingExchange: boolean;
    rejectReason: string;
    dialogErrorMessage: string | null;
    onRejectReasonChange: (reason: string) => void;
    onBeginDecline: () => void;
    onCancelDecline: () => void;
    onAccept: () => void;
    onReject: () => void;
    onDismiss: () => void;
}

const ExchangeAcceptanceDecisionControls = ({
    isSingleExchange,
    canDecideLater,
    updatingExchange,
    rejectingExchange,
    rejectReason,
    dialogErrorMessage,
    onRejectReasonChange,
    onBeginDecline,
    onCancelDecline,
    onAccept,
    onReject,
    onDismiss,
}: ExchangeAcceptanceDecisionControlsProps) =>
{
    const globalStyles = useGlobalStyles();
    const styles = useExchangeAcceptanceDialogStyles();
    return (
        <div id={'exchange-acceptance-decision-controls'}>
            {rejectingExchange && (
                <div className={styles.declineFieldContainer}>
                    <Field>
                        <Textarea
                            id={'textarea-acceptance-reject-reason'}
                            placeholder={'Reason for declining (optional)'}
                            value={rejectReason}
                            onChange={event => onRejectReasonChange(event.target.value)}
                            maxLength={100}
                        />
                    </Field>
                    <Checkbox
                        id={'checkbox-acceptance-report'}
                        label={'Report'}
                    />
                </div>
            )}
            {dialogErrorMessage && (
                <MessageBar
                    id={'exchange-acceptance-error'}
                    intent={'error'}
                >
                    <MessageBarBody>
                        <Text size={200}>{dialogErrorMessage}</Text>
                    </MessageBarBody>
                </MessageBar>
            )}
            <Divider/>
            <div className={styles.actions}>
                {!isSingleExchange && !rejectingExchange && canDecideLater && (
                    <div className={styles.tertiaryActions}>
                        <Button
                            id={'acceptance-decide-later-btn'}
                            appearance={'subtle'}
                            shape={'circular'}
                            disabled={updatingExchange}
                            onClick={onDismiss}
                        >
                            Decide Later
                        </Button>
                    </div>
                )}
                <div className={styles.primaryActions}>
                    {rejectingExchange ? (
                        <>
                            <Button
                                id={'acceptance-confirm-decline-btn'}
                                appearance={'primary'}
                                className={globalStyles.buttonWithLoading}
                                shape={'circular'}
                                disabled={updatingExchange}
                                onClick={onReject}
                            >
                                {updatingExchange && <Spinner size={'tiny'}/>}
                                Confirm Decline
                            </Button>
                            <Button
                                id={'acceptance-cancel-decline-btn'}
                                appearance={'secondary'}
                                shape={'circular'}
                                disabled={updatingExchange}
                                onClick={onCancelDecline}
                            >
                                Cancel
                            </Button>
                        </>
                    ) : (
                        <>
                            <Button
                                id={'acceptance-accept-btn'}
                                appearance={'primary'}
                                disabled={updatingExchange}
                                className={globalStyles.buttonWithLoading}
                                shape={'circular'}
                                onClick={onAccept}
                            >
                                {updatingExchange && <Spinner size={'tiny'}/>}
                                Accept
                            </Button>
                            <Button
                                id={'acceptance-decline-btn'}
                                appearance={'secondary'}
                                shape={'circular'}
                                disabled={updatingExchange}
                                onClick={onBeginDecline}
                            >
                                Decline
                            </Button>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ExchangeAcceptanceDecisionControls;
