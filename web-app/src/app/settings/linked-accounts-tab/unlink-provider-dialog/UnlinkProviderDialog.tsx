import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {providerDefinitions} from "../providerDefinitions.ts";
import {useUnlinkProviderDialogStyles} from "./UnlinkProviderDialogStyles.tsx";

interface UnlinkProviderDialogProps
{
    provider: string | null;
    unlinking: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

const UnlinkProviderDialog = ({provider, unlinking, onConfirm, onCancel}: UnlinkProviderDialogProps) =>
{
    const styles = useUnlinkProviderDialogStyles();
    const providerTitle = providerDefinitions.find(definition => definition.provider === provider)?.title
        ?? "this sign-in method";

    return (
        <Dialog
            open={provider !== null}
            onOpenChange={(_, data) => !data.open && !unlinking && onCancel()}>
            <DialogSurface id={"unlink-provider-dialog-surface"}>
                <DialogBody id={"unlink-provider-dialog-body"}>
                    <DialogTitle id={"unlink-provider-dialog-title"}>
                        Unlink {providerTitle}?
                    </DialogTitle>
                    <DialogContent
                        id={"unlink-provider-dialog-content"}
                        className={styles.content}>
                        <Text id={"unlink-provider-dialog-message"}>
                            You will no longer be able to sign in with {providerTitle}. You can link
                            this sign-in method again later.
                        </Text>
                    </DialogContent>
                    <DialogActions id={"unlink-provider-dialog-actions"}>
                        <Button
                            id={"unlink-provider-confirm-btn"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={unlinking}
                            onClick={onConfirm}>
                            {unlinking ? <><Spinner
                                id={"unlink-provider-spinner"}
                                size={"tiny"}
                            /> Unlinking</> : "Unlink"}
                        </Button>
                        <Button
                            id={"unlink-provider-cancel-btn"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={unlinking}
                            onClick={onCancel}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default UnlinkProviderDialog;
