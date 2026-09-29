import {Button, Text} from "@fluentui/react-components";
import {InformationRequestClockPolicyDto} from "../../../models/models.tsx";
import {useClockPoliciesPanelStyles} from "../clock-policies/ClockPoliciesPanelStyles.tsx";
import {clockPolicySentence, latestVersion} from "../clock-policies/clockPolicyLabels.ts";

interface ClockPolicyRowProps
{
    policy: InformationRequestClockPolicyDto;
    canManage: boolean;
    busy: boolean;
    onPublish: () => void;
}

const ClockPolicyRow = ({policy, canManage, busy, onPublish}: ClockPolicyRowProps) =>
{
    const styles = useClockPoliciesPanelStyles();
    const latest = latestVersion(policy.versions);
    const id = `information-request-clock-policy-${policy.id}`;

    return (
        <li id={id}
            className={styles.row}>
            <div id={`${id}-text`}
                 className={styles.text}>
                <Text id={`${id}-name`}
                      weight={"semibold"}>
                    {policy.displayName}
                </Text>
                <Text id={`${id}-version`}
                      size={200}
                      className={styles.muted}>
                    {latest
                        ? `Version ${latest.versionNumber} of ${policy.versions.length}, key ${policy.policyKey}`
                        : `No published version, key ${policy.policyKey}`}
                </Text>
                {latest && (
                    <Text id={`${id}-summary`}>{clockPolicySentence(latest)}</Text>
                )}
            </div>
            {canManage && (
                <Button id={`${id}-publish`}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={busy}
                        aria-label={`Publish a new version of ${policy.displayName}`}
                        onClick={onPublish}>
                    Publish new version
                </Button>
            )}
        </li>
    );
};

export default ClockPolicyRow;
