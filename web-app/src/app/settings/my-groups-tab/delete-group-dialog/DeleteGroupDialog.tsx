import {Button, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface, DialogTitle} from '@fluentui/react-components';

interface DeleteGroupDialogProps
{
    open: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

const DeleteGroupDialog = ({open, onConfirm, onCancel}: DeleteGroupDialogProps) => (
    <Dialog modalType="alert"
            open={open}>
        <DialogSurface id={"my-groups-tab-dialog-surface-4"}>
            <DialogBody id={"my-groups-tab-dialog-body-4"}>
                <DialogTitle id={"my-groups-tab-dialog-title-4"}>Delete Group</DialogTitle>
                <DialogContent id={"my-groups-tab-dialog-content-4"}>
                    Are you sure you want to delete this group? This action cannot be undone.
                </DialogContent>
                <DialogActions id={"my-groups-tab-dialog-actions-4"}>
                    <Button
                        id="confirm-delete-group-submit"
                        appearance="primary"
                        shape={"circular"}
                        onClick={onConfirm}
                    >
                        Delete
                    </Button>
                    <Button
                        id="confirm-delete-group-cancel"
                        appearance="secondary"
                        shape={"circular"}
                        onClick={onCancel}
                    >
                        Cancel
                    </Button>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>
);

export default DeleteGroupDialog;
