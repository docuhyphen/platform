import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {DocumentLibraryEntrySummaryDto} from "../../models/models.tsx";

interface Props
{
    entry: DocumentLibraryEntrySummaryDto;
    deleting: boolean;
    onConfirm: () => void;
    onDismiss: () => void;
}

const PlatformDocumentDeleteDialog = ({entry, deleting, onConfirm, onDismiss}: Props) => (
    <Dialog
        open={true}
        modalType={"alert"}
        onOpenChange={(_, data) =>
        {
            if (!data.open) onDismiss();
        }}>
        <DialogSurface id={"platform-document-delete-dialog"}>
            <DialogBody>
                <DialogTitle>Delete platform document?</DialogTitle>
                <DialogContent>
                    {entry.title} will be permanently removed. Blueprints that reference it will
                    lose the file association.
                </DialogContent>
                <DialogActions>
                    <Button
                        id={"platform-document-delete-confirm"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={deleting}
                        onClick={onConfirm}>
                        {deleting ? "Deleting..." : "Delete"}
                    </Button>
                    <Button
                        id={"platform-document-delete-cancel"}
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

export default PlatformDocumentDeleteDialog;
