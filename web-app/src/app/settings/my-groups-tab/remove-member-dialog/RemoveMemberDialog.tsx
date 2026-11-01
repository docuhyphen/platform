import {Button, Dialog, DialogActions, DialogBody, DialogContent, DialogSurface, DialogTitle} from '@fluentui/react-components';
import {RemoveMemberTarget} from '../myGroupsTypes.ts';

interface RemoveMemberDialogProps
{
    target: RemoveMemberTarget | null;
    onConfirm: () => void;
    onCancel: () => void;
}

const RemoveMemberDialog = ({target, onConfirm, onCancel}: RemoveMemberDialogProps) => (
    <Dialog modalType="alert"
            open={!!target}>
        <DialogSurface id={"my-groups-tab-dialog-surface-3"}>
            <DialogBody id={"my-groups-tab-dialog-body-3"}>
                <DialogTitle id={"my-groups-tab-dialog-title-3"}>Remove Member</DialogTitle>
                <DialogContent id={"my-groups-tab-dialog-content-3"}>
                    Are you sure you want to remove <strong id={"my-groups-tab-strong-1"}>{target?.displayName}</strong> from this group?
                </DialogContent>
                <DialogActions id={"my-groups-tab-dialog-actions-3"}>
                    <Button
                        id="confirm-remove-member-submit"
                        appearance="primary"
                        shape={"circular"}
                        onClick={onConfirm}
                    >
                        Remove
                    </Button>
                    <Button
                        id="confirm-remove-member-cancel"
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

export default RemoveMemberDialog;
