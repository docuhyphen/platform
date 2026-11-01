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
    Switch,
} from "@fluentui/react-components";
import React from "react";
import {OrganizationDetailedDto, OrganizationGroupDetailedDto} from "../../../models/models.tsx";
import {useEditGroupDialogStyles} from "./EditGroupDialogStyles.tsx";
import {useEditGroupDialog} from "./useEditGroupDialog.ts";
import EditGroupDialogMessages from "./EditGroupDialogMessages.tsx";
import GroupMembersEditor from "../group-members-editor/GroupMembersEditor.tsx";
import {editGroupEditorIds} from "../group-members-editor/groupMembersEditorIds.ts";

interface EditGroupDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    appUserPersonOrganization: OrganizationDetailedDto;
    group: OrganizationGroupDetailedDto;
    onComplete: () => void;
}

const EditGroupDialog: React.FC<EditGroupDialogProps> = (
    {
        isOpen,
        onDismiss,
        appUserPersonOrganization,
        group,
        onComplete
    }) =>
{
    const styles = useEditGroupDialogStyles();
    const dialog = useEditGroupDialog({isOpen, appUserPersonOrganization, group, onDismiss, onComplete});

    return (
        <Dialog modalType="alert"
                open={isOpen}>
            <DialogSurface id={"edit-group-dialog-dialog-surface-1"}>
                <DialogBody id={"edit-group-dialog-dialog-body-1"}>
                    <DialogTitle id={"edit-group-dialog-dialog-title-1"}
                                 className={styles.dialogTitleContainer}>
                        <span id={"edit-group-dialog-span-1"}>Edit Group</span>
                        <Field id={"edit-group-dialog-field-1"}>
                            <Switch
                                id="edit-group-dialog-active-switch"
                                checked={dialog.isActive}
                                onChange={(_, data) => dialog.setIsActive(data.checked)}
                                label={dialog.isActive ? "Disable" : "Enable"}
                            />
                        </Field>
                    </DialogTitle>
                    <DialogContent id={"edit-group-dialog-dialog-content-1"}
                                   className={styles.dialogContentContainer}>
                        <EditGroupDialogMessages permissionDenied={dialog.permissionDenied}
                                                 error={dialog.error}
                                                 onDismissError={() => dialog.setError(null)}/>

                        <Field id={"edit-group-dialog-field-2"}
                               label="Group Name"
                               required>
                            <Input
                                id={"edit-group-dialog-input-1"}
                                type="text"
                                value={dialog.name}
                                maxLength={80}
                                onChange={(e) => dialog.setName(e.target.value)}
                            />
                        </Field>

                        {dialog.selection.loadingUsers ? (
                            <Spinner id={"edit-group-dialog-spinner-1"}
                                     size="tiny"
                                     label="Loading users..."/>
                        ) : (
                            <GroupMembersEditor ids={editGroupEditorIds}
                                                selection={dialog.selection}
                                                disabled={dialog.permissionDenied}/>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"edit-group-dialog-dialog-actions-1"}>
                    <Button
                        id="edit-group-dialog-save"
                        appearance="primary"
                        shape="circular"
                        disabled={dialog.permissionDenied || dialog.savingData}
                        onClick={dialog.handleSave}>
                        {dialog.savingData && <Spinner id={"edit-group-dialog-spinner-2"}
                                                       size="tiny"/>}
                        Update Group
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id="edit-group-dialog-cancel"
                            appearance="secondary"
                            shape="circular"
                            disabled={dialog.savingData}
                            onClick={() => dialog.onClose()}>
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default EditGroupDialog;
