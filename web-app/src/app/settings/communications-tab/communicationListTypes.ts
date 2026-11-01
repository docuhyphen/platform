import {CommunicationSummaryDto} from '../../models/models';

export type CommunicationListTab = 'PERSONAL' | 'ORG' | 'PLATFORM';

export interface CommunicationListActions
{
    onEdit: (communication: CommunicationSummaryDto) => void;
    onPublish: (communication: CommunicationSummaryDto) => void;
    onActivate: (communication: CommunicationSummaryDto) => void;
    onDuplicate: (communication: CommunicationSummaryDto) => void;
    onDelete: (communication: CommunicationSummaryDto) => void;
}

export interface CommunicationListItemContext
{
    showPublication: boolean;
    canManageItem: (communication: CommunicationSummaryDto) => boolean;
    actions: CommunicationListActions;
}
