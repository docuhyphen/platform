import React from 'react';
import {Text} from '@fluentui/react-components';
import ExchangeEmptyDetailsFrame from '../exchange-empty-details-frame/ExchangeEmptyDetailsFrame.tsx';
import ExchangeEmptyDetailsActions from '../exchange-empty-details-actions/ExchangeEmptyDetailsActions.tsx';
import {ExchangeEmptyDetailsProps} from './exchangeEmptyDetailsProps.ts';

const ArchiveEmptyDetails: React.FC<ExchangeEmptyDetailsProps> = ({tabCounts, onOpenTab}) =>
{
    return (
        <ExchangeEmptyDetailsFrame sectionId={"exchanges-no-exchange-selected-section-4"}
                                   contentId={"exchanges-inbox-empty-details-content-4"}
                                   titleId={"exchanges-archive-is-empty-text"}
                                   title={"Archive is empty"}>
            <Text id={"exchanges-you-do-not-have-text-3"}
                  size={300}
                  align={"center"}>
                You do not have archived Exchanges yet. Check <Text id={"exchanges-inbox-text-2"}
                                                                    weight={"semibold"}>Inbox</Text>
                {' '}for pending requests or open <Text id={"exchanges-active-text-3"}
                                                        weight={"semibold"}>Active</Text> exchanges.
            </Text>
            <ExchangeEmptyDetailsActions id={"exchanges-inbox-empty-actions-4"}
                                         primary={{
                                             id: "exchanges-archive-empty-open-inbox-btn",
                                             label: "Open Inbox",
                                             tab: 'inbox',
                                             count: tabCounts.inbox,
                                         }}
                                         secondary={{
                                             id: "exchanges-archive-empty-open-active-btn",
                                             label: "Open Active",
                                             tab: 'active',
                                             count: tabCounts.active,
                                         }}
                                         onOpenTab={onOpenTab}/>
        </ExchangeEmptyDetailsFrame>
    );
};

export default ArchiveEmptyDetails;
