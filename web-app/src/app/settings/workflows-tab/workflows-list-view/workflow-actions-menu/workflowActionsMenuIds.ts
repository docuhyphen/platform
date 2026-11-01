export type WorkflowActionsMenuVariant = 'card' | 'table';

export interface WorkflowActionsMenuIds
{
    trigger: (definitionId: string) => string;
    popover: string;
    list: string;
    edit: string;
    publish: string;
    active: string;
    clone: string;
    remove: string;
}

export const WORKFLOW_ACTIONS_MENU_IDS: Record<WorkflowActionsMenuVariant, WorkflowActionsMenuIds> = {
    card: {
        trigger: definitionId => `workflow-card-more-actions-btn-${definitionId}`,
        popover: "workflows-list-view-menu-popover-1",
        list: "workflows-list-view-menu-list-1",
        edit: "workflows-list-view-menu-item-1",
        publish: "workflows-list-view-menu-item-2",
        active: "workflows-list-view-menu-item-3",
        clone: "workflows-list-view-menu-item-4",
        remove: "workflows-list-view-menu-item-5",
    },
    table: {
        trigger: definitionId => `workflows-table-more-actions-btn-${definitionId}`,
        popover: "workflows-list-view-menu-popover-2",
        list: "workflows-list-view-menu-list-2",
        edit: "workflows-list-view-menu-item-6",
        publish: "workflows-list-view-menu-item-7",
        active: "workflows-list-view-menu-item-8",
        clone: "workflows-list-view-menu-item-9",
        remove: "workflows-list-view-menu-item-10",
    },
};
