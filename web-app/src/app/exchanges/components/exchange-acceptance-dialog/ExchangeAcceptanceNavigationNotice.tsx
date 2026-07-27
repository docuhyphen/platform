import {Button, MessageBar, MessageBarBody, Text} from '@fluentui/react-components';
import {InfoRegular} from '@fluentui/react-icons';
import {useExchangeAcceptanceDialogStyles} from './ExchangeAcceptanceDialogStyles.tsx';

interface ExchangeAcceptanceNavigationNoticeProps
{
    isSingleExchange: boolean;
    canDecideLater: boolean;
    rejectingExchange: boolean;
    updatingExchange: boolean;
    activeCount: number;
    archiveCount: number;
    onOpenActive: () => void;
    onOpenArchive: () => void;
}

const ExchangeAcceptanceNavigationNotice = ({
    isSingleExchange,
    canDecideLater,
    rejectingExchange,
    updatingExchange,
    activeCount,
    archiveCount,
    onOpenActive,
    onOpenArchive,
}: ExchangeAcceptanceNavigationNoticeProps) =>
{
    const styles = useExchangeAcceptanceDialogStyles();
    if (rejectingExchange || canDecideLater)
    {
        return null;
    }
    return (
        <div id={'exchange-acceptance-navigation-notice'}>
            <MessageBar
                id={'exchange-acceptance-last-request-message'}
                intent={'info'}
                icon={<InfoRegular/>}
            >
                <MessageBarBody>
                    <Text size={200}>This is your last pending request.</Text>
                </MessageBarBody>
            </MessageBar>
            {!isSingleExchange && (
                <div className={styles.navigationActions}>
                    <Button
                        id={'acceptance-open-active-btn'}
                        appearance={'secondary'}
                        shape={'circular'}
                        disabled={updatingExchange || activeCount === 0}
                        onClick={onOpenActive}
                    >
                        Open Active ({activeCount})
                    </Button>
                    <Button
                        id={'acceptance-open-archive-btn'}
                        appearance={'secondary'}
                        shape={'circular'}
                        disabled={updatingExchange || archiveCount === 0}
                        onClick={onOpenArchive}
                    >
                        Open Archive ({archiveCount})
                    </Button>
                </div>
            )}
        </div>
    );
};

export default ExchangeAcceptanceNavigationNotice;
