export interface VariableIdScheme
{
    tab: string;
    button: string;
    input: string;
}

export interface VariableMenuIds
{
    trigger: string;
    popover: string;
    list: string;
    edit: string;
    remove: string;
}

export const organizationVariableIds: VariableIdScheme = {
    tab: 'organization-variables-tab',
    button: 'button-org-var',
    input: 'input-org-var',
};

export const personalVariableIds: VariableIdScheme = {
    tab: 'personal-variables-tab',
    button: 'button-personal-var',
    input: 'input-personal-var',
};

export const variableCardMenuIds = (ids: VariableIdScheme, variableId: string): VariableMenuIds => ({
    trigger: `${ids.button}-menu-${variableId}`,
    popover: `${ids.tab}-menu-popover-1`,
    list: `${ids.tab}-menu-list-1`,
    edit: `${ids.tab}-menu-item-1`,
    remove: `${ids.tab}-menu-item-2`,
});

export const variableTableMenuIds = (ids: VariableIdScheme, variableId: string): VariableMenuIds => ({
    trigger: `${ids.button}-menu-table-${variableId}`,
    popover: `${ids.tab}-menu-popover-2`,
    list: `${ids.tab}-menu-list-2`,
    edit: `${ids.tab}-menu-item-3`,
    remove: `${ids.tab}-menu-item-4`,
});
