import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Spinner,
} from "@fluentui/react-components";
import React from "react";
import {useAddGroupDialogStyles} from "./AddGroupDialogStyles.tsx";
import {useAddGroupDialog} from "./useAddGroupDialog.ts";
import GroupMembersEditor from "../group-members-editor/GroupMembersEditor.tsx";
import {addGroupEditorIds} from "../group-members-editor/groupMembersEditorIds.ts";

interface AddGroupDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    organizationId: string;
    onComplete: () => void;
}

const AddGroupDialog: React.FC<AddGroupDialogProps> = (
    {
        isOpen,
        onDismiss,
        organizationId,
        onComplete
    }) =>
{
    const styles = useAddGroupDialogStyles();
    const {name, setName, savingData, error, selection, handleSave, onClose} =
        useAddGroupDialog({isOpen, organizationId, onDismiss, onComplete});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"add-group-dialog-dialog-surface-1"}>
                <DialogBody id={"add-group-dialog-dialog-body-1"}>
                    <DialogTitle id={"add-group-dialog-dialog-title-1"}>Create New Group</DialogTitle>
                    <DialogContent id={"add-group-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        {error && (
                            <div id={"add-group-dialog-div-1"}
                                 className={styles.errorMessage}>
                                {error}
                            </div>
                        )}

                        <Field id={"add-group-dialog-field-1"}
                               label="Group Name"
                               required>
                            <Input
                                id={"add-group-dialog-input-1"}
                                type="text"
                                value={name}
                                maxLength={80}
                                onChange={(e) => setName(e.target.value)}
                            />
                        </Field>

                        <Field id={"add-group-dialog-field-2"}
                               label="Group Members"
                               required>
                            {selection.loadingUsers ? (
                                <Spinner id={"add-group-dialog-spinner-1"}
                                         size="tiny"
                                         label="Loading users..."/>
                            ) : (
                                <GroupMembersEditor ids={addGroupEditorIds}
                                                    selection={selection}/>
                            )}
                        </Field>
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"add-group-dialog-dialog-actions-1"}>
                    <Button
                        id="add-group-dialog-create"
                        appearance="primary"
                        shape="circular"
                        disabled={savingData || !name.trim() || selection.selectedMembers.size === 0}
                        onClick={handleSave}
                    >
                        {savingData && <Spinner id={"add-group-dialog-spinner-2"}
                                                size="tiny"/>}
                        Create Group
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id="add-group-dialog-cancel"
                            appearance="secondary"
                            shape="circular"
                            disabled={savingData}
                            onClick={onClose}
                        >
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default AddGroupDialog;
