import {useEffect, useState} from "react";
import {useAuth} from "../../../context/AuthContext.tsx";
import {PlatformApiError} from "../../../services/platformOrganizationApi.ts";
import {PlatformFeatureEntitlement} from "../../../services/types/platformFeatureEntitlements.ts";
import {
    PlatformUserSubscriptionPolicy,
    PlatformUserSubscriptionPolicyRequest
} from "../../../services/types/platformUserSubscriptions.ts";
import {FeatureOverrideDraft, FeatureOverrideLoader, useFeatureOverrideDraft} from "./user-feature-overrides/useFeatureOverrideDraft.ts";
import {UserSubscriptionFieldValues} from "./UserSubscriptionFields.tsx";

export interface UserSubscriptionEditorDialogProps
{
    user: PlatformUserSubscriptionPolicy | null;
    onDismiss: () => void;
    onSave: (user: PlatformUserSubscriptionPolicy, request: PlatformUserSubscriptionPolicyRequest) => Promise<void>;
    onLoadFeatureOverrides: FeatureOverrideLoader;
    onSaveFeatureOverrides: (user: PlatformUserSubscriptionPolicy, overrides: PlatformFeatureEntitlement[], changeReason: string) => Promise<void>;
}

export interface UserSubscriptionEditorState
{
    values: UserSubscriptionFieldValues;
    saving: boolean;
    error: string | null;
    overrides: FeatureOverrideDraft;
    change: <K extends keyof UserSubscriptionFieldValues>(key: K, value: UserSubscriptionFieldValues[K]) => void;
    save: () => Promise<void>;
}

const inputDate = (value: string | null): string => value ? value.slice(0, 16) : "";
const isoDate = (value: string): string | null => value ? new Date(value).toISOString() : null;

const emptyValues: UserSubscriptionFieldValues = {
    planCode: "FREE",
    status: "ACTIVE",
    billingFrequency: "",
    periodStart: "",
    periodEnd: "",
    graceEnd: "",
    reason: ""
};

const validationProblem = (values: UserSubscriptionFieldValues): string | null =>
{
    if (!values.reason.trim()) return "Change reason is required.";
    if ((values.periodStart && !values.periodEnd) || (!values.periodStart && values.periodEnd))
    {
        return "Current period start and end must be provided together.";
    }
    if ((values.status === "TRIALING" || values.status === "CANCELED") && !values.periodEnd)
    {
        return "Trialing and canceled subscriptions require a period end.";
    }
    if (values.status === "PAST_DUE" && !values.graceEnd) return "Past-due subscriptions require a grace period end.";
    return null;
};

export const useUserSubscriptionEditor = ({
                                              user,
                                              onDismiss,
                                              onSave,
                                              onLoadFeatureOverrides,
                                              onSaveFeatureOverrides
                                          }: UserSubscriptionEditorDialogProps): UserSubscriptionEditorState =>
{
    const {refreshCurrentSession} = useAuth();
    const [values, setValues] = useState<UserSubscriptionFieldValues>(emptyValues);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const overrides = useFeatureOverrideDraft(user, onLoadFeatureOverrides);

    useEffect(() =>
    {
        if (!user) return;
        setValues({
            planCode: user.planCode,
            status: user.subscriptionStatus,
            billingFrequency: user.billingFrequency ?? "",
            periodStart: inputDate(user.currentPeriodStart),
            periodEnd: inputDate(user.currentPeriodEnd),
            graceEnd: inputDate(user.gracePeriodEnd),
            reason: ""
        });
        setError(null);
    }, [user]);

    const change = <K extends keyof UserSubscriptionFieldValues>(key: K, value: UserSubscriptionFieldValues[K]) =>
        setValues((current) => ({...current, [key]: value}));

    const save = async () =>
    {
        if (!user) return;
        const problem = validationProblem(values);
        if (problem)
        {
            setError(problem);
            return;
        }
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
        catch (saveError: unknown)
        {
            setError((saveError as PlatformApiError).errorMessage || "Failed to update subscription.");
        }
        finally
        {
            setSaving(false);
        }
    };

    return {values, saving, error, overrides, change, save};
};
