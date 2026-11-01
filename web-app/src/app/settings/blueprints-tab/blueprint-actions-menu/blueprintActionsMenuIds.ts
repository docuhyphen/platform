import {BlueprintMenuVariant} from '../blueprintListTypes.ts';

export interface BlueprintActionsMenuIds
{
    buttonPrefix: string;
    popover: string;
    list: string;
    edit: string;
    publish: string;
    activate: string;
    cloneToPersonal: string;
    cloneToOrg: string;
    duplicate: string;
    remove: string;
}

export const blueprintActionsMenuIds: Record<BlueprintMenuVariant, BlueprintActionsMenuIds> = {
    card: {
        buttonPrefix: 'button-blueprint-more-',
        popover: 'blueprints-tab-menu-popover-3',
        list: 'blueprints-tab-menu-list-3',
        edit: 'blueprints-tab-menu-item-6',
        publish: 'blueprints-tab-menu-item-7',
        activate: 'blueprints-tab-menu-item-8',
        cloneToPersonal: 'blueprints-tab-menu-item-9',
        cloneToOrg: 'blueprints-tab-menu-item-10',
        duplicate: 'blueprints-tab-menu-item-11',
        remove: 'blueprints-tab-menu-item-12',
    },
    table: {
        buttonPrefix: 'button-blueprint-more-table-',
        popover: 'blueprints-tab-menu-popover-5',
        list: 'blueprints-tab-menu-list-5',
        edit: 'blueprints-tab-menu-item-15',
        publish: 'blueprints-tab-menu-item-16',
        activate: 'blueprints-tab-menu-item-17',
        cloneToPersonal: 'blueprints-tab-menu-item-18',
        cloneToOrg: 'blueprints-tab-menu-item-19',
        duplicate: 'blueprints-tab-menu-item-20',
        remove: 'blueprints-tab-menu-item-21',
    },
};
