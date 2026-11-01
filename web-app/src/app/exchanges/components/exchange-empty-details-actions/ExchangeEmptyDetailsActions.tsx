import React from 'react';
import {Button} from '@fluentui/react-components';
import {ExchangeListTab} from '../exchange-list/exchange-list-tabs/ExchangeListTabs.tsx';
import {useExchangeEmptyDetailsActionsStyles} from './ExchangeEmptyDetailsActionsStyles.tsx';

export interface ExchangeEmptyDetailsAction
{
    id: string;
    label: string;
    tab: ExchangeListTab;
    count: number;
}

interface ExchangeEmptyDetailsActionsProps
{
    id: string;
    primary: ExchangeEmptyDetailsAction;
    secondary: ExchangeEmptyDetailsAction;
    onOpenTab: (tab: ExchangeListTab) => void;
}

const ExchangeEmptyDetailsActions: React.FC<ExchangeEmptyDetailsActionsProps> = ({id, primary, secondary, onOpenTab}) =>
{
    const styles = useExchangeEmptyDetailsActionsStyles();

    return (
        <div id={id}
             className={styles.inboxEmptyActions}>
            <Button
                id={primary.id}
                appearance="primary"
                shape={"circular"}
                disabled={primary.count === 0}
                onClick={() => onOpenTab(primary.tab)}>
                {primary.label} ({primary.count})
            </Button>
            <Button
                id={secondary.id}
                appearance="secondary"
                shape={"circular"}
                disabled={secondary.count === 0}
                onClick={() => onOpenTab(secondary.tab)}>
                {secondary.label} ({secondary.count})
            </Button>
        </div>
    );
};

export default ExchangeEmptyDetailsActions;
