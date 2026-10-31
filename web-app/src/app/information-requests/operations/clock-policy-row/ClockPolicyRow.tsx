import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {InformationRequestClockPolicyDto} from "../../../models/models.tsx";
import {clockPolicySentence, latestVersion} from "../clock-policies/clockPolicyLabels.ts";
import ClockPolicyVersions from "../clock-policy-versions/ClockPolicyVersions.tsx";
import {useClockPolicyRowStyles} from "./ClockPolicyRowStyles.tsx";

interface ClockPolicyRowProps
{
    policy: InformationRequestClockPolicyDto;
    canManage: boolean;
    busy: boolean;
    onPublish: () => void;
}

const ClockPolicyRow = ({policy, canManage, busy, onPublish}: ClockPolicyRowProps) =>
{
    const styles = useClockPolicyRowStyles();
    const [showingVersions, setShowingVersions] = useState(false);
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
            <div id={`${id}-actions`}
                 className={styles.actions}>
                {policy.versions.length > 0 && (
                    <Button id={`${id}-versions-btn`}
                            appearance={"secondary"}
                            shape={"circular"}
                            aria-label={`Show the versions of ${policy.displayName}`}
                            aria-expanded={showingVersions}
                            aria-controls={`${id}-versions`}
                            onClick={() => setShowingVersions(current => !current)}>
                        Versions
                    </Button>
                )}
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
            </div>
            {showingVersions && (
                <ClockPolicyVersions id={`${id}-versions`}
                                     policy={policy}/>
            )}
        </li>
    );
};

export default ClockPolicyRow;
