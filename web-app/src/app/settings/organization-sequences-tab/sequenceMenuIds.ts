export interface SequenceMenuIds
{
    trigger: string;
    popover: string;
    list: string;
    edit: string;
    reset: string;
    remove: string;
}

export const sequenceCardMenuIds = (sequenceId: string): SequenceMenuIds => ({
    trigger: `button-seq-menu-${sequenceId}`,
    popover: 'organization-sequences-tab-menu-popover-1',
    list: 'organization-sequences-tab-menu-list-1',
    edit: 'organization-sequences-tab-menu-item-1',
    reset: 'organization-sequences-tab-menu-item-2',
    remove: 'organization-sequences-tab-menu-item-3',
});

export const sequenceTableMenuIds = (sequenceId: string): SequenceMenuIds => ({
    trigger: `button-seq-menu-table-${sequenceId}`,
    popover: 'organization-sequences-tab-menu-popover-2',
    list: 'organization-sequences-tab-menu-list-2',
    edit: 'organization-sequences-tab-menu-item-4',
    reset: 'organization-sequences-tab-menu-item-5',
    remove: 'organization-sequences-tab-menu-item-6',
});
