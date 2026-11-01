import React from 'react';
import {Text} from '@fluentui/react-components';
import ExchangeEmptyDetailsFrame from '../exchange-empty-details-frame/ExchangeEmptyDetailsFrame.tsx';
import ExchangeEmptyDetailsActions from '../exchange-empty-details-actions/ExchangeEmptyDetailsActions.tsx';
import {getExchangeCountLabel} from './exchangeCountLabel.ts';
import {ExchangeEmptyDetailsProps} from './exchangeEmptyDetailsProps.ts';

const OutgoingInboxEmptyDetails: React.FC<ExchangeEmptyDetailsProps> = ({tabCounts, onOpenTab}) =>
{
    return (
        <ExchangeEmptyDetailsFrame sectionId={"exchanges-no-exchange-selected-section-2"}
                                   contentId={"exchanges-inbox-empty-details-content-2"}
                                   titleId={"exchanges-no-outgoing-requests-text"}
                                   title={"No outgoing requests"}>
            <Text id={"exchanges-you-do-not-have-text"}
                  size={300}
                  align={"center"}>
                You do not have requests awaiting recipient response right now. You have <Text
                id={"exchanges-get-exchange-count-label-text-3"}
                weight={"semibold"}>{getExchangeCountLabel(tabCounts.active, 'active')}</Text>
                {' '}and <Text id={"exchanges-get-exchange-count-label-text-4"}
                               weight={"semibold"}>{getExchangeCountLabel(tabCounts.archive, 'archived')}</Text>.
                Open <Text id={"exchanges-active-text-2"}
                           weight={"semibold"}>Active</Text> or <Text id={"exchanges-archive-text-2"}
                                                                      weight={"semibold"}>Archive</Text>,
                or start a new <Text id={"exchanges-request-or-send-text-2"}
                                     weight={"semibold"}>Request or Send</Text> from Start Exchange.
            </Text>
            <ExchangeEmptyDetailsActions id={"exchanges-inbox-empty-actions-2"}
                                         primary={{
                                             id: "exchanges-outgoing-empty-open-active-btn",
                                             label: "Open Active",
                                             tab: 'active',
                                             count: tabCounts.active,
                                         }}
                                         secondary={{
                                             id: "exchanges-outgoing-empty-open-archive-btn",
                                             label: "Open Archive",
                                             tab: 'archive',
                                             count: tabCounts.archive,
                                         }}
                                         onOpenTab={onOpenTab}/>
        </ExchangeEmptyDetailsFrame>
    );
};

export default OutgoingInboxEmptyDetails;
