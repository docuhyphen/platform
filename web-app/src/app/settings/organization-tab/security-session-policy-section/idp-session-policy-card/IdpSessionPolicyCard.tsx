import {Badge, Button, Text} from "@fluentui/react-components";
import type {OrgAuthSessionPolicyIdp} from "../../../../../services/authApi.ts";
import {DraftRow, DraftValueKey} from "../securitySessionPolicyDraft.ts";
import IdpSessionPolicyInputs from "./IdpSessionPolicyInputs.tsx";
import {useIdpSessionPolicyCardStyles} from "./IdpSessionPolicyCardStyles.tsx";

interface IdpSessionPolicyCardProps
{
    row: OrgAuthSessionPolicyIdp;
    draft: DraftRow;
    onDraftChange: (configId: string, patch: Partial<DraftRow>) => void;
    onSave: (configId: string) => void;
    onReset: (configId: string) => void;
}

const IdpSessionPolicyCard = ({row, draft, onDraftChange, onSave, onReset}: IdpSessionPolicyCardProps) =>
{
    const styles = useIdpSessionPolicyCardStyles();
    const disabled = !row.isActive || draft.saving;

    const onValueChange = (key: DraftValueKey, value: string) =>
    {
        const patch: Partial<DraftRow> = {error: null};
        patch[key] = value;
        onDraftChange(row.configId, patch);
    };

    return (
        <div
            id={`security-session-policy-section-div-3-${row.configId}`}
            className={styles.idpCard}
        >
            <div id={"security-session-policy-section-div-4"}
                 className={styles.idpCardHeader}>
                <Text id={"security-session-policy-section-text-2"}
                      weight="semibold">{row.provider}</Text>
                <Badge
                    id={"security-session-policy-section-badge-1"}
                    appearance="outline"
                    color={row.isActive ? "success" : "subtle"}
                >
                    {row.isActive ? "Active" : "Inactive"}
                </Badge>
            </div>

            <IdpSessionPolicyInputs draft={draft}
                                    disabled={disabled}
                                    onValueChange={onValueChange}/>

            {draft.error && (
                <div
                    id={"security-session-policy-section-div-6"}
                    className={styles.draftError}
                >
                    {draft.error}
                </div>
            )}

            <div id={"security-session-policy-section-div-7"}
                 className={styles.actionRow}>
                <Button
                    id={`security-session-policy-save-${row.configId}`}
                    appearance="primary"
                    shape="circular"
                    size="small"
                    disabled={disabled}
                    onClick={() => onSave(row.configId)}
                >
                    {draft.saving ? "Saving..." : "Save"}
                </Button>
                <Button
                    id={`security-session-policy-reset-${row.configId}`}
                    appearance="secondary"
                    shape="circular"
                    size="small"
                    disabled={disabled}
                    onClick={() => onReset(row.configId)}
                >
                    Reset
                </Button>
            </div>
        </div>
    );
};

export default IdpSessionPolicyCard;
