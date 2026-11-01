import React from 'react';
import {Text} from '@fluentui/react-components';
import ExchangeEmptyDetailsFrame from '../exchange-empty-details-frame/ExchangeEmptyDetailsFrame.tsx';
import ExchangeEmptyDetailsActions from '../exchange-empty-details-actions/ExchangeEmptyDetailsActions.tsx';
import {ExchangeEmptyDetailsProps} from './exchangeEmptyDetailsProps.ts';

const ActiveEmptyDetails: React.FC<ExchangeEmptyDetailsProps> = ({tabCounts, onOpenTab}) =>
{
    return (
        <ExchangeEmptyDetailsFrame sectionId={"exchanges-no-exchange-selected-section-3"}
                                   contentId={"exchanges-inbox-empty-details-content-3"}
                                   titleId={"exchanges-no-active-exchanges-text"}
                                   title={"No active Exchanges"}>
            <Text id={"exchanges-you-do-not-have-text-2"}
                  size={300}
                  align={"center"}>
                You do not have active Exchanges right now. Check <Text id={"exchanges-inbox-text"}
                                                                        weight={"semibold"}>Inbox</Text>
                {' '}for new requests or review <Text id={"exchanges-archive-text-3"}
                                                      weight={"semibold"}>Archive</Text>.
            </Text>
            <ExchangeEmptyDetailsActions id={"exchanges-inbox-empty-actions-3"}
                                         primary={{
                                             id: "exchanges-active-empty-open-inbox-btn",
                                             label: "Open Inbox",
                                             tab: 'inbox',
                                             count: tabCounts.inbox,
                                         }}
                                         secondary={{
                                             id: "exchanges-active-empty-open-archive-btn",
                                             label: "Open Archive",
                                             tab: 'archive',
                                             count: tabCounts.archive,
                                         }}
                                         onOpenTab={onOpenTab}/>
        </ExchangeEmptyDetailsFrame>
    );
};

export default ActiveEmptyDetails;
