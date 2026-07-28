import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {CommunicationSummaryDto} from "../../models/models.tsx";

interface Props
{
    communication: CommunicationSummaryDto;
    deleting: boolean;
    onConfirm: () => void;
    onDismiss: () => void;
}

const PlatformCommunicationDeleteDialog = ({
    communication,
    deleting,
    onConfirm,
    onDismiss,
}: Props) => (
    <Dialog
        open={true}
        onOpenChange={(_, data) =>
        {
            if (!data.open) onDismiss();
        }}>
        <DialogSurface id={"platform-communication-delete-dialog"}>
            <DialogBody>
                <DialogTitle id={"platform-communication-delete-title"}>
                    Delete platform communication
                </DialogTitle>
                <DialogContent id={"platform-communication-delete-content"}>
                    Delete {communication.name}? This permanently removes the platform communication.
                </DialogContent>
                <DialogActions id={"platform-communication-delete-actions"}>
                    <Button
                        id={"platform-communication-delete-confirm"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={deleting}
                        onClick={onConfirm}>
                        Delete
                    </Button>
                    <Button
                        id={"platform-communication-delete-cancel"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={deleting}
                        onClick={onDismiss}>
                        Cancel
                    </Button>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>
);

export default PlatformCommunicationDeleteDialog;
