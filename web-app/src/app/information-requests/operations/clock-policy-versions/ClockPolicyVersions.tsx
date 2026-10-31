import {InformationRequestClockPolicyDto} from "../../../models/models.tsx";
import {clockPolicyVersionSentence} from "../clock-policies/clockPolicyLabels.ts";
import {useCommunicationNames} from "../clock-policy-notice-fields/useCommunicationNames.ts";
import {useClockPolicyVersionsStyles} from "./ClockPolicyVersionsStyles.tsx";

interface ClockPolicyVersionsProps {
    id: string;
    policy: InformationRequestClockPolicyDto;
}

const ClockPolicyVersions = ({id, policy}: ClockPolicyVersionsProps) => {
    const styles = useClockPolicyVersionsStyles();
    const versions = [...policy.versions].sort((first, second) => second.versionNumber - first.versionNumber);
    const {names} = useCommunicationNames(versions.flatMap(version =>
        [version.reminderCommunicationId ?? undefined, version.overdueCommunicationId ?? undefined]));

    return (
        <ol id={id}
            aria-label={`Versions of ${policy.displayName}`}
            className={styles.versions}>
            {versions.map(version => (
                <li id={`${id}-${version.versionNumber}`}
                    key={version.id}
                    className={styles.version}>
                    {clockPolicyVersionSentence(version, names)}
                </li>
            ))}
        </ol>
    );
};

export default ClockPolicyVersions;
