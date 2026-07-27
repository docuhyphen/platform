import {Button, Spinner} from '@fluentui/react-components';
import {useGlobalStyles} from '../../../../GlobalStyles.tsx';
import {useExchangeAcceptanceDialogStyles} from './ExchangeAcceptanceDialogStyles.tsx';

interface ExchangeAcceptanceActionButtonsProps
{
    id: string;
    isSingleExchange: boolean;
    canDecideLater: boolean;
    updatingExchange: boolean;
    rejectingExchange: boolean;
    onBeginDecline: () => void;
    onCancelDecline: () => void;
    onAccept: () => void;
    onReject: () => void;
    onDismiss: () => void;
}

const ExchangeAcceptanceActionButtons = ({
    id,
    isSingleExchange,
    canDecideLater,
    updatingExchange,
    rejectingExchange,
    onBeginDecline,
    onCancelDecline,
    onAccept,
    onReject,
    onDismiss,
}: ExchangeAcceptanceActionButtonsProps) =>
{
    const globalStyles = useGlobalStyles();
    const styles = useExchangeAcceptanceDialogStyles();

    return (
        <div
            id={id}
            className={styles.actions}
        >
            {!isSingleExchange && !rejectingExchange && canDecideLater && (
                <div
                    id={'exchange-acceptance-tertiary-actions'}
                    className={styles.tertiaryActions}
                >
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
            <div
                id={'exchange-acceptance-primary-actions'}
                className={styles.primaryActions}
            >
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
                            {updatingExchange && (
                                <Spinner
                                    id={'exchange-acceptance-decline-spinner'}
                                    size={'tiny'}
                                />
                            )}
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
                            {updatingExchange && (
                                <Spinner
                                    id={'exchange-acceptance-accept-spinner'}
                                    size={'tiny'}
                                />
                            )}
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
    );
};

export default ExchangeAcceptanceActionButtons;
