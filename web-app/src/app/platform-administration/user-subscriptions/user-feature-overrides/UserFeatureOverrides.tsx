import {MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import FeatureOverrideRow from "../../feature-override-row/FeatureOverrideRow.tsx";
import FeatureOverrideSelector from "../../feature-override-selector/FeatureOverrideSelector.tsx";
import {INDIVIDUAL_ACCOUNT_FEATURES} from "../../feature-override-selector/featureOverrideLabels.ts";
import {FeatureOverrideDraft} from "./useFeatureOverrideDraft.ts";
import {useUserFeatureOverridesStyles} from "./UserFeatureOverridesStyles.tsx";

interface UserFeatureOverridesProps {
    overrides: FeatureOverrideDraft;
    disabled: boolean;
}

const UserFeatureOverrides = ({overrides, disabled}: UserFeatureOverridesProps) => {
    const styles = useUserFeatureOverridesStyles();

    return (
        <div id={"platform-user-feature-overrides"}
             role={"group"}
             aria-labelledby={"platform-user-feature-overrides-title"}
             className={styles.overrides}>
            <Text id={"platform-user-feature-overrides-title"}
                  weight={"semibold"}>
                Feature overrides
            </Text>
            <Text id={"platform-user-feature-overrides-hint"}
                  size={200}
                  className={styles.muted}>
                An override grants a feature the plan leaves out or withholds one it includes. Removing an
                override returns the feature to the plan.
            </Text>
            {overrides.failed && (
                <MessageBar id={"platform-user-feature-overrides-error"}
                            intent={"warning"}>
                    <MessageBarBody id={"platform-user-feature-overrides-error-body"}>
                        The account&apos;s feature overrides could not be loaded, so they are left as they are.
                    </MessageBarBody>
                </MessageBar>
            )}
            {!overrides.failed && !overrides.loaded && (
                <Spinner id={"platform-user-feature-overrides-loading"}
                         size={"tiny"}
                         label={"Loading feature overrides"}/>
            )}
            {overrides.loaded && (
                <>
                    <FeatureOverrideSelector idPrefix={"platform-user"}
                                             ownerDescription={"this account"}
                                             features={INDIVIDUAL_ACCOUNT_FEATURES}
                                             selectedFeatures={overrides.draft.map(override => override.featureCode)}
                                             disabled={disabled}
                                             onChange={overrides.setFeatureCodes}/>
                    {overrides.draft.map((override, index) => (
                        <FeatureOverrideRow key={override.featureCode}
                                            idPrefix={"platform-user"}
                                            index={index}
                                            entitlement={override}
                                            disabled={disabled}
                                            onChange={overrides.update}
                                            onRemove={overrides.remove}/>
                    ))}
                </>
            )}
        </div>
    );
};

export default UserFeatureOverrides;
