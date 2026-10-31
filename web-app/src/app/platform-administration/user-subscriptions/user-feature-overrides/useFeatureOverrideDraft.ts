import {useEffect, useState} from "react";
import {PlatformFeatureEntitlement} from "../../../../services/types/platformFeatureEntitlements.ts";
import {PlatformUserSubscriptionPolicy} from "../../../../services/types/platformUserSubscriptions.ts";

export type FeatureOverrideLoader = (user: PlatformUserSubscriptionPolicy) => Promise<PlatformFeatureEntitlement[]>;

const sameOverrides = (first: PlatformFeatureEntitlement[], second: PlatformFeatureEntitlement[]): boolean =>
    first.length === second.length && first.every((override, index) =>
        override.featureCode === second[index].featureCode && override.enabled === second[index].enabled);

export const useFeatureOverrideDraft = (user: PlatformUserSubscriptionPolicy | null, load: FeatureOverrideLoader) => {
    const [original, setOriginal] = useState<PlatformFeatureEntitlement[] | null>(null);
    const [draft, setDraft] = useState<PlatformFeatureEntitlement[]>([]);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        setOriginal(null);
        setDraft([]);
        setFailed(false);
        if (!user) return;
        let current = true;
        load(user)
            .then(loaded => {
                if (!current) return;
                setOriginal(loaded);
                setDraft(loaded.map(override => ({...override})));
            })
            .catch(() => {
                if (current) setFailed(true);
            });
        return () => {
            current = false;
        };
    }, [user, load]);

    const setFeatureCodes = (featureCodes: string[]) =>
        setDraft(current => featureCodes.map(featureCode =>
            current.find(override => override.featureCode === featureCode) ?? {featureCode, enabled: true}));

    const update = (index: number, value: PlatformFeatureEntitlement) =>
        setDraft(current => current.map((override, overrideIndex) => overrideIndex === index ? value : override));

    const remove = (index: number) =>
        setDraft(current => current.filter((_, overrideIndex) => overrideIndex !== index));

    return {
        loaded: original !== null,
        failed,
        draft,
        changed: original !== null && !sameOverrides(original, draft),
        setFeatureCodes,
        update,
        remove,
    };
};

export type FeatureOverrideDraft = ReturnType<typeof useFeatureOverrideDraft>;
