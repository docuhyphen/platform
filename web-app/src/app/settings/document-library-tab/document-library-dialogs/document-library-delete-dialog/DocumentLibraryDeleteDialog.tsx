import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
} from '@fluentui/react-components';

interface DocumentLibraryDeleteDialogProps
{
    open: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

const DocumentLibraryDeleteDialog = ({open, onConfirm, onCancel}: DocumentLibraryDeleteDialogProps) => (
    <Dialog open={open}
            modalType="alert">
        <DialogSurface id={"document-library-tab-dialog-surface-1"}>
            <DialogBody id={"document-library-tab-dialog-body-1"}>
                <DialogTitle id={"document-library-tab-dialog-title-1"}>Delete document?</DialogTitle>
                <DialogContent id={"document-library-tab-dialog-content-1"}>
                    This document will be permanently removed from the library. Any blueprints that
                    reference it will lose the file association but will not be deleted.
                </DialogContent>
                <DialogActions id={"document-library-tab-dialog-actions-1"}>
                    <Button id="doc-library-delete-confirm-btn"
                            shape="circular"
                            appearance="primary"
                            onClick={onConfirm}>
                        Delete
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button id="doc-library-delete-cancel-btn"
                                shape="circular"
                                appearance="secondary"
                                onClick={onCancel}>
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogBody>
        </DialogSurface>
    </Dialog>
);

export default DocumentLibraryDeleteDialog;
