import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    MessageBar,
    MessageBarBody,
    Spinner
} from "@fluentui/react-components";
import UserFeatureOverrides from "./user-feature-overrides/UserFeatureOverrides.tsx";
import UserSubscriptionFields from "./UserSubscriptionFields.tsx";
import {useUserSubscriptionsStyles} from "./UserSubscriptionsStyles.tsx";
import {UserSubscriptionEditorDialogProps, useUserSubscriptionEditor} from "./useUserSubscriptionEditor.ts";

const UserSubscriptionEditorDialog = (props: UserSubscriptionEditorDialogProps) =>
{
    const {user, onDismiss} = props;
    const styles = useUserSubscriptionsStyles();
    const {values, saving, error, overrides, change, save} = useUserSubscriptionEditor(props);

    return (
        <Dialog
            open={user !== null}
            onOpenChange={(_, data) => !data.open && !saving && onDismiss()}>
            <DialogSurface id={"platform-user-subscription-editor-surface"}>
                <DialogBody
                    id={"platform-user-subscription-editor-body"}
                    className={styles.dialogBody}>
                    <DialogTitle id={"platform-user-subscription-editor-title"}>Edit {user?.email ?? "user"}</DialogTitle>
                    <DialogContent
                        id={"platform-user-subscription-editor-content"}
                        className={styles.dialogContent}>
                        {error && (
                            <MessageBar
                                id={"platform-user-subscription-editor-error"}
                                intent={"error"}>
                                <MessageBarBody id={"platform-user-subscription-editor-error-body"}>
                                    {error}
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <UserSubscriptionFields
                            values={values}
                            disabled={saving}
                            onChange={change}/>
                        <UserFeatureOverrides
                            overrides={overrides}
                            disabled={saving}/>
                    </DialogContent>
                    <DialogActions
                        id={"platform-user-subscription-editor-actions"}
                        className={styles.footer}>
                        <Button
                            id={"platform-user-subscription-editor-save"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={saving}
                            onClick={() => { void save(); }}>
                            {saving
                                ? (
                                    <Spinner id={"platform-user-subscription-editor-saving"}
                                             size={"tiny"}/>
                                )
                                : "Save"}
                        </Button>
                        <Button
                            id={"platform-user-subscription-editor-cancel"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={saving}
                            onClick={onDismiss}>
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default UserSubscriptionEditorDialog;
