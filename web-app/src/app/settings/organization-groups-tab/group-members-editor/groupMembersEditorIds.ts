export interface GroupMembersEditorIds
{
    field: string;
    picker: string;
    emptyText: string;
    tablePrefix: string;
    nameCellDiv: string;
    emailCellDiv: string;
    rolePrefix: string;
    removePrefix: string;
    optionPrefix: string;
}

export const addGroupEditorIds: GroupMembersEditorIds = {
    field: "add-group-dialog-field-3",
    picker: "add-group-member-picker",
    emptyText: "add-group-dialog-text-1",
    tablePrefix: "add-group-dialog",
    nameCellDiv: "add-group-dialog-div-2",
    emailCellDiv: "add-group-dialog-div-3",
    rolePrefix: "add-group-member-role",
    removePrefix: "add-group-remove-member",
    optionPrefix: "add-group-dialog-option-1",
};

export const editGroupEditorIds: GroupMembersEditorIds = {
    field: "edit-group-dialog-field-3",
    picker: "edit-group-member-picker",
    emptyText: "edit-group-dialog-text-1",
    tablePrefix: "edit-group-dialog",
    nameCellDiv: "edit-group-dialog-div-1",
    emailCellDiv: "edit-group-dialog-div-2",
    rolePrefix: "edit-group-member-role",
    removePrefix: "edit-group-remove-member",
    optionPrefix: "edit-group-dialog-option-1",
};
