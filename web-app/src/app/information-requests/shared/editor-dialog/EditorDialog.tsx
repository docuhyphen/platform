import React from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {useEditorDialogStyles} from "./EditorDialogStyles.tsx";

interface EditorDialogProps
{
    id: string;
    title: string;
    readOnly?: boolean;
    busy?: boolean;
    confirmLabel?: string;
    dismissLabel?: string;
    confirmDisabled?: boolean;
    wide?: boolean;
    children: React.ReactNode;
    onConfirm: () => void;
    onDismiss: () => void;
}

const EditorDialog = ({
    id,
    title,
    readOnly,
    busy,
    confirmLabel = "Save",
    dismissLabel = "Cancel",
    confirmDisabled,
    wide,
    children,
    onConfirm,
    onDismiss,
}: EditorDialogProps) =>
{
    const styles = useEditorDialogStyles();

    return (
        <Dialog open={true}
                onOpenChange={(_, data) => { if (!data.open) onDismiss(); }}>
            <DialogSurface id={id}
                           aria-labelledby={`${id}-title`}
                           className={wide ? styles.wideSurface : styles.surface}>
                <DialogBody>
                    <DialogTitle id={`${id}-title`}>{title}</DialogTitle>
                    <DialogContent id={`${id}-content`}
                                   className={styles.content}>
                        {children}
                    </DialogContent>
                    <DialogActions id={`${id}-actions`}
                                   className={styles.actions}>
                        {readOnly
                            ? (
                                <Button id={`${id}-close`}
                                        appearance={"primary"}
                                        shape={"circular"}
                                        onClick={onDismiss}>
                                    Close
                                </Button>
                            )
                            : (
                                <>
                                    <Button id={`${id}-confirm`}
                                            appearance={"primary"}
                                            shape={"circular"}
                                            disabled={busy || confirmDisabled}
                                            onClick={onConfirm}>
                                        {confirmLabel}
                                    </Button>
                                    <Button id={`${id}-cancel`}
                                            appearance={"secondary"}
                                            shape={"circular"}
                                            disabled={busy}
                                            onClick={onDismiss}>
                                        {dismissLabel}
                                    </Button>
                                </>
                            )}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default EditorDialog;
