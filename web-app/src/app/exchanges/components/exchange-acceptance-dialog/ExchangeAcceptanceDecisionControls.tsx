import {Checkbox, Divider, Field, MessageBar, MessageBarBody, Text, Textarea}
    from '@fluentui/react-components';
import {useExchangeAcceptanceDialogStyles} from './ExchangeAcceptanceDialogStyles.tsx';
import ExchangeAcceptanceActionButtons from './ExchangeAcceptanceActionButtons.tsx';

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
    const styles = useExchangeAcceptanceDialogStyles();
    return (
        <div id={'exchange-acceptance-decision-controls'}>
            {rejectingExchange && (
                <div
                    id={'exchange-acceptance-decline-field-container'}
                    className={styles.declineFieldContainer}
                >
                    <Field id={'exchange-acceptance-reject-reason-field'}>
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
                    <MessageBarBody id={'exchange-acceptance-error-body'}>
                        <Text
                            id={'exchange-acceptance-error-text'}
                            size={200}
                        >
                            {dialogErrorMessage}
                        </Text>
                    </MessageBarBody>
                </MessageBar>
            )}
            <Divider id={'exchange-acceptance-actions-divider'}/>
            <ExchangeAcceptanceActionButtons
                id={'exchange-acceptance-actions'}
                isSingleExchange={isSingleExchange}
                canDecideLater={canDecideLater}
                updatingExchange={updatingExchange}
                rejectingExchange={rejectingExchange}
                onBeginDecline={onBeginDecline}
                onCancelDecline={onCancelDecline}
                onAccept={onAccept}
                onReject={onReject}
                onDismiss={onDismiss}
            />
        </div>
    );
};

export default ExchangeAcceptanceDecisionControls;
