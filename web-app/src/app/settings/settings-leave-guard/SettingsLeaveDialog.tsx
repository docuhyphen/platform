import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";

interface Props {
    open: boolean;
    title?: string;
    onLeave: () => void;
    onStay: () => void;
}

const SettingsLeaveDialog = ({open, title = "Leave without saving?", onLeave, onStay}: Props) => (
    <Dialog open={open}
            onOpenChange={(_, data) => {
                if (!data.open) onStay();
            }}>
        <DialogSurface id={"settings-leave-dialog"}>
            <DialogBody id={"settings-leave-dialog-body"}>
                <DialogTitle id={"settings-leave-dialog-title"}>{title}</DialogTitle>
                <DialogContent id={"settings-leave-dialog-content"}>
                    You have unsaved changes. If you leave now they will be lost.
                </DialogContent>
                <DialogActions id={"settings-leave-dialog-actions"}>
                    <Button id={"settings-leave-dialog-discard"}
                            appearance={"primary"}
                            shape={"circular"}
                            onClick={onLeave}>
                        Discard changes
                    </Button>
                    <Button id={"settings-leave-dialog-stay"}
                            appearance={"secondary"}
                            shape={"circular"}
                            onClick={onStay}>
                        Keep editing
                    </Button>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>
);

export default SettingsLeaveDialog;
