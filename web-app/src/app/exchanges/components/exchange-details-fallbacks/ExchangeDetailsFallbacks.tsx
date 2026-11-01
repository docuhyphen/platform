import React from 'react';
import ExchangeSelectionHint from '../exchange-selection-hint/ExchangeSelectionHint.tsx';
import IncomingInboxEmptyDetails from './IncomingInboxEmptyDetails.tsx';
import OutgoingInboxEmptyDetails from './OutgoingInboxEmptyDetails.tsx';
import ActiveEmptyDetails from './ActiveEmptyDetails.tsx';
import ArchiveEmptyDetails from './ArchiveEmptyDetails.tsx';
import {ExchangeDetailsFallbackFlags} from './exchangeDetailsFallbacks.ts';
import {ExchangeEmptyDetailsProps} from './exchangeEmptyDetailsProps.ts';

interface ExchangeDetailsFallbacksProps extends ExchangeEmptyDetailsProps
{
    fallbacks: ExchangeDetailsFallbackFlags;
}

const ExchangeDetailsFallbacks: React.FC<ExchangeDetailsFallbacksProps> = ({fallbacks, tabCounts, onOpenTab}) =>
{
    return (
        <>
            {fallbacks.showIncomingInboxEmpty &&
                <IncomingInboxEmptyDetails tabCounts={tabCounts}
                                           onOpenTab={onOpenTab}/>
            }

            {fallbacks.showOutgoingInboxEmpty &&
                <OutgoingInboxEmptyDetails tabCounts={tabCounts}
                                           onOpenTab={onOpenTab}/>
            }

            {fallbacks.showActiveEmpty &&
                <ActiveEmptyDetails tabCounts={tabCounts}
                                    onOpenTab={onOpenTab}/>
            }

            {fallbacks.showArchiveEmpty &&
                <ArchiveEmptyDetails tabCounts={tabCounts}
                                     onOpenTab={onOpenTab}/>
            }

            {fallbacks.showSelectionHint && <ExchangeSelectionHint/>}
        </>
    );
};

export default ExchangeDetailsFallbacks;
