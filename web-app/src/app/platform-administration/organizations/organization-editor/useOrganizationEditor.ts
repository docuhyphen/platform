import {useEffect, useState} from "react";
import {
    PlatformApiError,
    updatePlatformOrganizationFeatureEntitlements,
    updatePlatformOrganizationSubscriptionPolicy,
} from "../../../../services/platformOrganizationApi.ts";
import {
    PlatformOrganizationFeatureEntitlement,
    PlatformOrganizationSummary,
} from "../../../../services/types/platformOrganizations.ts";

const validate = (
    tierCode: string,
    maxUsers: string,
    entitlements: PlatformOrganizationFeatureEntitlement[],
): string | null =>
{
    if (!tierCode.trim()) return "Tier code is required.";
    if (maxUsers && (!Number.isInteger(Number(maxUsers)) || Number(maxUsers) <= 0))
        return "Licensed capacity must be a whole number greater than zero.";
    const codes = entitlements.map((item) => item.featureCode.trim().toUpperCase());
    if (codes.some((code) => !/^[A-Z][A-Z0-9_]{0,63}$/.test(code)))
        return "Feature codes must use uppercase letters, numbers, and underscores.";
    if (new Set(codes).size !== codes.length) return "Feature codes must be unique.";
    return null;
};

export const useOrganizationEditor = (
    organization: PlatformOrganizationSummary | null,
    onSaved: () => void,
) =>
{
    const [tierCode, setTierCode] = useState("");
    const [maxUsers, setMaxUsers] = useState("");
    const [changeReason, setChangeReason] = useState("");
    const [entitlements, setEntitlements] = useState<PlatformOrganizationFeatureEntitlement[]>([]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() =>
    {
        if (!organization) return;
        setTierCode(organization.tierCode);
        setMaxUsers(organization.maxUsers?.toString() ?? "");
        setChangeReason("");
        setEntitlements(organization.featureEntitlements.map((item) => ({...item})));
        setError(null);
    }, [organization]);

    const updateEntitlement = (index: number, value: PlatformOrganizationFeatureEntitlement) =>
        setEntitlements((current) => current.map((item, itemIndex) => itemIndex === index ? value : item));

    const removeEntitlement = (index: number) =>
        setEntitlements((current) => current.filter((_, itemIndex) => itemIndex !== index));

    const addEntitlement = () =>
        setEntitlements((current) => [...current, {featureCode: "", enabled: true}]);

    const save = async () =>
    {
        if (!organization) return;
        const validationError = validate(tierCode, maxUsers, entitlements);
        if (validationError)
        {
            setError(validationError);
            return;
        }
        setSaving(true);
        setError(null);
        try
        {
            const reason = changeReason.trim() || undefined;
            await updatePlatformOrganizationSubscriptionPolicy(organization.organizationId, {
                tierCode: tierCode.trim().toUpperCase(),
                maxUsers: maxUsers ? Number(maxUsers) : null,
                changeReason: reason,
            });
            await updatePlatformOrganizationFeatureEntitlements(organization.organizationId, {
                entitlements: entitlements.map((item) => ({
                    featureCode: item.featureCode.trim().toUpperCase(),
                    enabled: item.enabled,
                })),
                changeReason: reason,
            });
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

    return {
        tierCode,
        maxUsers,
        changeReason,
        entitlements,
        saving,
        error,
        setTierCode,
        setMaxUsers,
        setChangeReason,
        updateEntitlement,
        removeEntitlement,
        addEntitlement,
        save,
    };
};
