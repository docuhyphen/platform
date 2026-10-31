import {useEffect, useState} from "react";
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
import {useAuth} from "../../../context/AuthContext.tsx";
import {PlatformApiError} from "../../../services/platformOrganizationApi.ts";
import {PlatformFeatureEntitlement} from "../../../services/types/platformFeatureEntitlements.ts";
import {
    PlatformUserSubscriptionPolicy,
    PlatformUserSubscriptionPolicyRequest
} from "../../../services/types/platformUserSubscriptions.ts";
import UserFeatureOverrides from "./user-feature-overrides/UserFeatureOverrides.tsx";
import {FeatureOverrideLoader, useFeatureOverrideDraft} from "./user-feature-overrides/useFeatureOverrideDraft.ts";
import UserSubscriptionFields, {UserSubscriptionFieldValues} from "./UserSubscriptionFields.tsx";
import {useUserSubscriptionsStyles} from "./UserSubscriptionsStyles.tsx";

interface UserSubscriptionEditorDialogProps
{
    user: PlatformUserSubscriptionPolicy | null;
    onDismiss: () => void;
    onSave: (user: PlatformUserSubscriptionPolicy, request: PlatformUserSubscriptionPolicyRequest) => Promise<void>;
    onLoadFeatureOverrides: FeatureOverrideLoader;
    onSaveFeatureOverrides: (user: PlatformUserSubscriptionPolicy, overrides: PlatformFeatureEntitlement[], changeReason: string) => Promise<void>;
}

const inputDate = (value: string | null): string => value ? value.slice(0, 16) : "";
const isoDate = (value: string): string | null => value ? new Date(value).toISOString() : null;

const UserSubscriptionEditorDialog = ({
                                          user,
                                          onDismiss,
                                          onSave,
                                          onLoadFeatureOverrides,
                                          onSaveFeatureOverrides
                                      }: UserSubscriptionEditorDialogProps) =>
{
    const styles = useUserSubscriptionsStyles();
    const {refreshCurrentSession} = useAuth();
    const [values, setValues] = useState<UserSubscriptionFieldValues>({planCode: "FREE", status: "ACTIVE", billingFrequency: "", periodStart: "", periodEnd: "", graceEnd: "", reason: ""});
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const overrides = useFeatureOverrideDraft(user, onLoadFeatureOverrides);

    useEffect(() =>
    {
        if (!user) return;
        setValues({planCode: user.planCode, status: user.subscriptionStatus, billingFrequency: user.billingFrequency ?? "", periodStart: inputDate(user.currentPeriodStart), periodEnd: inputDate(user.currentPeriodEnd), graceEnd: inputDate(user.gracePeriodEnd), reason: ""});
        setError(null);
    }, [user]);

    const change = <K extends keyof UserSubscriptionFieldValues>(key: K, value: UserSubscriptionFieldValues[K]) =>
        setValues((current) => ({...current, [key]: value}));

    const save = async () =>
    {
        if (!user) return;
        if (!values.reason.trim()) { setError("Change reason is required."); return; }
        if ((values.periodStart && !values.periodEnd) || (!values.periodStart && values.periodEnd)) { setError("Current period start and end must be provided together."); return; }
        if ((values.status === "TRIALING" || values.status === "CANCELED") && !values.periodEnd) { setError("Trialing and canceled subscriptions require a period end."); return; }
        if (values.status === "PAST_DUE" && !values.graceEnd) { setError("Past-due subscriptions require a grace period end."); return; }
        setSaving(true);
        setError(null);
        try
        {
            const reason = values.reason.trim();
            await onSave(user, {
                planCode: values.planCode,
                subscriptionStatus: values.status,
                billingFrequency: values.billingFrequency as PlatformUserSubscriptionPolicy["billingFrequency"] || null,
                currentPeriodStart: isoDate(values.periodStart),
                currentPeriodEnd: isoDate(values.periodEnd),
                gracePeriodEnd: values.status === "PAST_DUE" ? isoDate(values.graceEnd) : null,
                changeReason: reason
            });
            if (overrides.changed) await onSaveFeatureOverrides(user, overrides.draft, reason);
            await refreshCurrentSession();
            onDismiss();
        }
        catch (saveError: unknown) { setError((saveError as PlatformApiError).errorMessage || "Failed to update subscription."); }
        finally { setSaving(false); }
    };

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
                            onClick={() => void save()}>
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
