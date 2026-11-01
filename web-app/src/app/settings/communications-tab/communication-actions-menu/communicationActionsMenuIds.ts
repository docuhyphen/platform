export type CommunicationMenuVariant = 'card' | 'table';

export interface CommunicationActionsMenuIds
{
    trigger: (communicationId: string) => string;
    popover: string;
    list: string;
    edit: string;
    publish: string;
    activate: string;
    duplicate: string;
    remove: string;
}

export const communicationActionsMenuIds: Record<CommunicationMenuVariant, CommunicationActionsMenuIds> = {
    card: {
        trigger: communicationId => `button-communication-more-${communicationId}`,
        popover: 'communications-tab-menu-popover-1',
        list: 'communications-tab-menu-list-1',
        edit: 'communications-tab-menu-item-1',
        publish: 'communications-tab-menu-item-2',
        activate: 'communications-tab-menu-item-3',
        duplicate: 'communications-tab-menu-item-4',
        remove: 'communications-tab-menu-item-5',
    },
    table: {
        trigger: communicationId => `button-communication-table-more-${communicationId}`,
        popover: 'communications-tab-menu-popover-2',
        list: 'communications-tab-menu-list-2',
        edit: 'communications-tab-menu-item-6',
        publish: 'communications-tab-menu-item-7',
        activate: 'communications-tab-menu-item-8',
        duplicate: 'communications-tab-menu-item-9',
        remove: 'communications-tab-menu-item-10',
    },
};
