import {useEffect, useState} from "react";
import {
    PlatformApiError,
    updatePlatformOrganizationFeatureEntitlements,
    updatePlatformOrganizationStatus,
    updatePlatformOrganizationSubscriptionPolicy,
} from "../../../../services/platformOrganizationApi.ts";
import {
    PlatformOrganizationFeatureEntitlement,
    PlatformOrganizationSummary,
} from "../../../../services/types/platformOrganizations.ts";

const validate = (
    tierCode: string,
    maxUsers: string,
    subscriptionStatus: string,
    currentPeriodStart: string,
    currentPeriodEnd: string,
    gracePeriodEnd: string,
    changeReason: string,
    entitlements: PlatformOrganizationFeatureEntitlement[],
): string | null =>
{
    if (!tierCode.trim()) return "Tier code is required.";
    if (!changeReason.trim()) return "Change reason is required for audited subscription updates.";
    if (maxUsers && (!Number.isInteger(Number(maxUsers)) || Number(maxUsers) <= 0))
        return "Purchased seats must be a whole number greater than zero.";
    if ((currentPeriodStart && !currentPeriodEnd) || (!currentPeriodStart && currentPeriodEnd))
        return "Current period start and end must be provided together.";
    if ((subscriptionStatus === "TRIALING" || subscriptionStatus === "CANCELED") && !currentPeriodEnd)
        return `${subscriptionStatus === "TRIALING" ? "Trialing" : "Canceled"} subscriptions require a period end.`;
    if (subscriptionStatus === "PAST_DUE" && !gracePeriodEnd)
        return "Past-due subscriptions require a grace period end.";
    const codes = entitlements.map((item) => item.featureCode.trim().toUpperCase());
    if (codes.some((code) => !/^[A-Z][A-Z0-9_]{0,63}$/.test(code)))
        return "Feature codes must use uppercase letters, numbers, and underscores.";
    if (new Set(codes).size !== codes.length) return "Feature codes must be unique.";
    return null;
};

const toDateTimeInput = (value: string | null): string => value ? value.slice(0, 16) : "";
const toIsoInstant = (value: string): string | null => value ? new Date(value).toISOString() : null;

export const useOrganizationEditor = (
    organization: PlatformOrganizationSummary | null,
    onSaved: () => void,
    refreshCurrentSession: () => Promise<unknown>,
) =>
{
    const [tierCode, setTierCode] = useState("");
    const [maxUsers, setMaxUsers] = useState("");
    const [subscriptionStatus, setSubscriptionStatus] = useState("ACTIVE");
    const [billingFrequency, setBillingFrequency] = useState("");
    const [currentPeriodStart, setCurrentPeriodStart] = useState("");
    const [currentPeriodEnd, setCurrentPeriodEnd] = useState("");
    const [gracePeriodEnd, setGracePeriodEnd] = useState("");
    const [active, setActive] = useState(false);
    const [verificationComplete, setVerificationComplete] = useState(false);
    const [changeReason, setChangeReason] = useState("");
    const [entitlements, setEntitlements] = useState<PlatformOrganizationFeatureEntitlement[]>([]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!organization) return;
        setTierCode(organization.tierCode);
        setMaxUsers(organization.maxUsers?.toString() ?? "");
        setSubscriptionStatus(organization.subscriptionStatus);
        setBillingFrequency(organization.billingFrequency ?? "");
        setCurrentPeriodStart(toDateTimeInput(organization.currentPeriodStart));
        setCurrentPeriodEnd(toDateTimeInput(organization.currentPeriodEnd));
        setGracePeriodEnd(toDateTimeInput(organization.gracePeriodEnd));
        setActive(organization.active);
        setVerificationComplete(organization.verificationComplete);
        setChangeReason("");
        setEntitlements(organization.featureEntitlements.map((item) => ({...item})));
        setError(null);
    }, [organization]);

    const updateEntitlement = (index: number, value: PlatformOrganizationFeatureEntitlement) =>
        setEntitlements((current) => current.map((item, itemIndex) => itemIndex === index ? value : item));

    const removeEntitlement = (index: number) =>
        setEntitlements((current) => current.filter((_, itemIndex) => itemIndex !== index));

    const setEntitlementFeatureCodes = (featureCodes: string[]) =>
        setEntitlements((current) => featureCodes.map((featureCode) =>
            current.find((entitlement) => entitlement.featureCode === featureCode)
            ?? {featureCode, enabled: true},
        ));

    const save = async () =>
    {
        if (!organization) return;
        const validationError = validate(
            tierCode,
            maxUsers,
            subscriptionStatus,
            currentPeriodStart,
            currentPeriodEnd,
            gracePeriodEnd,
            changeReason,
            entitlements,
        );
        if (validationError)
        {
            setError(validationError);
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            const reason = changeReason.trim();
            await updatePlatformOrganizationSubscriptionPolicy(organization.organizationId, {
                tierCode: tierCode.trim().toUpperCase(),
                maxUsers: maxUsers ? Number(maxUsers) : null,
                subscriptionStatus,
                billingFrequency: billingFrequency || null,
                currentPeriodStart: toIsoInstant(currentPeriodStart),
                currentPeriodEnd: toIsoInstant(currentPeriodEnd),
                gracePeriodEnd: subscriptionStatus === "PAST_DUE" ? toIsoInstant(gracePeriodEnd) : null,
                changeReason: reason,
            });
            await updatePlatformOrganizationFeatureEntitlements(organization.organizationId, {
                entitlements: entitlements.map((item) => ({
                    featureCode: item.featureCode.trim().toUpperCase(),
                    enabled: item.enabled,
                })),
                changeReason: reason,
            });
            if (
                active !== organization.active
                || verificationComplete !== organization.verificationComplete
            )
            {
                await updatePlatformOrganizationStatus(organization.organizationId, {
                    active,
                    verificationComplete,
                    changeReason: reason,
                });
            }
            await refreshCurrentSession();
            onSaved();
        }
        catch (saveError: unknown)
        {
            setError((saveError as PlatformApiError).errorMessage || "Failed to update organization account.");
        }
        finally
        {
            setSaving(false);
        }
    };

    const activeSeats = organization?.activeUsers ?? 0;
    const purchasedSeats = maxUsers && Number.isInteger(Number(maxUsers)) ? Number(maxUsers) : null;
    const remainingSeats = purchasedSeats == null ? null : Math.max(purchasedSeats - activeSeats, 0);

    return {
        tierCode,
        maxUsers,
        subscriptionStatus,
        billingFrequency,
        currentPeriodStart,
        currentPeriodEnd,
        gracePeriodEnd,
        active,
        verificationComplete,
        changeReason,
        entitlements,
        saving,
        error,
        activeSeats,
        remainingSeats,
        setTierCode,
        setMaxUsers,
        setSubscriptionStatus,
        setBillingFrequency,
        setCurrentPeriodStart,
        setCurrentPeriodEnd,
        setGracePeriodEnd,
        setActive,
        setVerificationComplete,
        setChangeReason,
        updateEntitlement,
        removeEntitlement,
        setEntitlementFeatureCodes,
        save,
    };
};
