import {BlueprintMenuVariant} from '../blueprintListTypes.ts';

export interface BlueprintCloneMenuIds
{
    buttonPrefix: string;
    popover: string;
    list: string;
    cloneToPersonal: string;
    cloneToOrg: string;
}

export const blueprintCloneMenuIds: Record<BlueprintMenuVariant, BlueprintCloneMenuIds> = {
    card: {
        buttonPrefix: 'button-blueprint-more-',
        popover: 'blueprints-tab-menu-popover-2',
        list: 'blueprints-tab-menu-list-2',
        cloneToPersonal: 'blueprints-tab-menu-item-4',
        cloneToOrg: 'blueprints-tab-menu-item-5',
    },
    table: {
        buttonPrefix: 'button-blueprint-more-table-readonly-',
        popover: 'blueprints-tab-menu-popover-4',
        list: 'blueprints-tab-menu-list-4',
        cloneToPersonal: 'blueprints-tab-menu-item-13',
        cloneToOrg: 'blueprints-tab-menu-item-14',
    },
};
