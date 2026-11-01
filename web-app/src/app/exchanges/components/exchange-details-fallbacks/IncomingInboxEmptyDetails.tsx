import React from 'react';
import {Text} from '@fluentui/react-components';
import ExchangeEmptyDetailsFrame from '../exchange-empty-details-frame/ExchangeEmptyDetailsFrame.tsx';
import ExchangeEmptyDetailsActions from '../exchange-empty-details-actions/ExchangeEmptyDetailsActions.tsx';
import {getExchangeCountLabel} from './exchangeCountLabel.ts';
import {ExchangeEmptyDetailsProps} from './exchangeEmptyDetailsProps.ts';

const IncomingInboxEmptyDetails: React.FC<ExchangeEmptyDetailsProps> = ({tabCounts, onOpenTab}) =>
{
    return (
        <ExchangeEmptyDetailsFrame sectionId={"exchanges-no-exchange-selected-section"}
                                   contentId={"exchanges-inbox-empty-details-content"}
                                   titleId={"exchanges-no-incoming-requests-text"}
                                   title={"No incoming requests"}>
            <Text id={"exchanges-you-have-no-pending-text"}
                  size={300}
                  align={"center"}>
                You have no pending requests right now. You have <Text
                id={"exchanges-get-exchange-count-label-text"}
                weight={"semibold"}>{getExchangeCountLabel(tabCounts.active, 'active')}</Text>
                {' '}and <Text id={"exchanges-get-exchange-count-label-text-2"}
                               weight={"semibold"}>{getExchangeCountLabel(tabCounts.archive, 'archived')}</Text>.
                Open <Text id={"exchanges-active-text"}
                           weight={"semibold"}>Active</Text> or <Text id={"exchanges-archive-text"}
                                                                      weight={"semibold"}>Archive</Text>,
                or start a new <Text id={"exchanges-request-or-send-text"}
                                     weight={"semibold"}>Request or Send</Text> from Start Exchange.
            </Text>
            <ExchangeEmptyDetailsActions id={"exchanges-inbox-empty-actions"}
                                         primary={{
                                             id: "exchanges-incoming-empty-open-active-btn",
                                             label: "Open Active",
                                             tab: 'active',
                                             count: tabCounts.active,
                                         }}
                                         secondary={{
                                             id: "exchanges-incoming-empty-open-archive-btn",
                                             label: "Open Archive",
                                             tab: 'archive',
                                             count: tabCounts.archive,
                                         }}
                                         onOpenTab={onOpenTab}/>
        </ExchangeEmptyDetailsFrame>
    );
};

export default IncomingInboxEmptyDetails;
