import {Input, Text} from "@fluentui/react-components";
import {DraftRow, DraftValueKey} from "../securitySessionPolicyDraft.ts";
import {useIdpSessionPolicyCardStyles} from "./IdpSessionPolicyCardStyles.tsx";

interface IdpSessionPolicyInputsProps
{
    draft: DraftRow;
    disabled: boolean;
    onValueChange: (key: DraftValueKey, value: string) => void;
}

interface PolicyInputDefinition
{
    key: DraftValueKey;
    label: string;
    index: number;
}

const policyInputs: PolicyInputDefinition[] = [
    {key: "accessTokenExpiryMinutes", label: "Access token expiry (minutes)", index: 1},
    {key: "refreshTokenExpiryMinutes", label: "Refresh token expiry (minutes)", index: 2},
    {key: "maxSessionDurationHours", label: "Max session duration (hours)", index: 3},
    {key: "idleTimeoutMinutes", label: "Idle timeout (minutes)", index: 4},
];

const IdpSessionPolicyInputs = ({draft, disabled, onValueChange}: IdpSessionPolicyInputsProps) =>
{
    const styles = useIdpSessionPolicyCardStyles();

    return (
        <div
            id={"security-session-policy-section-div-5"}
            className={styles.inputsGrid}
        >
            {policyInputs.map(({key, label, index}) => (
                <label id={`security-session-policy-section-label-${index}`}
                       key={key}
                       className={styles.inputLabel}>
                    <Text id={`security-session-policy-section-text-${index + 2}`}
                          size={200}>{label}</Text>
                    <Input
                        id={`security-session-policy-section-input-${index}`}
                        value={draft[key]}
                        disabled={disabled}
                        placeholder="(inherit default)"
                        onChange={(_, d) => onValueChange(key, d.value)}
                    />
                </label>
            ))}
        </div>
    );
};

export default IdpSessionPolicyInputs;
