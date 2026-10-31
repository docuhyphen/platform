import {useState} from "react";
import {Button, Spinner, Text} from "@fluentui/react-components";
import {InformationRequestClockPolicyDto, InformationRequestOwnerType} from "../../../models/models.tsx";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import ClockPolicyDialog from "../clock-policy-dialog/ClockPolicyDialog.tsx";
import ClockPolicyRow from "../clock-policy-row/ClockPolicyRow.tsx";
import {useClockPolicies} from "./useClockPolicies.ts";
import {useClockPoliciesPanelStyles} from "./ClockPoliciesPanelStyles.tsx";

interface ClockPoliciesPanelProps
{
    canManage: boolean;
    ownerType: InformationRequestOwnerType;
}

type Editing = { kind: "define" } | { kind: "publish"; policy: InformationRequestClockPolicyDto };

const ClockPoliciesPanel = ({canManage, ownerType}: ClockPoliciesPanelProps) =>
{
    const styles = useClockPoliciesPanelStyles();
    const state = useClockPolicies();
    const [editing, setEditing] = useState<Editing | null>(null);

    return (
        <section id={"information-request-clock-policies"}
                 aria-labelledby={"information-request-clock-policies-title"}
                 className={styles.panel}>
            <div id={"information-request-clock-policies-header"}
                 className={styles.header}>
                <Text id={"information-request-clock-policies-title"}
                      as={"h2"}
                      size={500}
                      weight={"semibold"}
                      className={styles.heading}>
                    Due date policies
                </Text>
                {canManage && state.policies && (
                    <Button id={"information-request-clock-policies-new"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={state.busy}
                            onClick={() => setEditing({kind: "define"})}>
                        New policy
                    </Button>
                )}
            </div>
            <Text id={"information-request-clock-policies-explanation"}
                  className={styles.muted}>
                A due date policy says how long a request has, when reminders go out, and what happens when it is
                due. Each change publishes a new version; clocks already running keep the version they started with.
            </Text>
            {state.loadError && (
                <InformationRequestLoadError id={"information-request-clock-policies-error"}
                                             message={state.loadError}
                                             onRetry={() => void state.reload()}/>
            )}
            <Text id={"information-request-clock-policies-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {state.notice ?? ""}
            </Text>
            {!state.policies && !state.loadError && (
                <Spinner id={"information-request-clock-policies-loading"}
                         size={"medium"}
                         label={"Loading due date policies"}/>
            )}
            {state.policies && state.policies.length === 0 && (
                <Text id={"information-request-clock-policies-empty"}>No due date policy has been created.</Text>
            )}
            {state.policies && state.policies.length > 0 && (
                <ul id={"information-request-clock-policies-list"}
                    aria-labelledby={"information-request-clock-policies-title"}
                    className={styles.list}>
                    {state.policies.map(policy => (
                        <ClockPolicyRow key={policy.id}
                                        policy={policy}
                                        canManage={canManage}
                                        busy={state.busy}
                                        onPublish={() => setEditing({kind: "publish", policy})}/>
                    ))}
                </ul>
            )}
            {editing && (
                <ClockPolicyDialog policy={editing.kind === "publish" ? editing.policy : undefined}
                                   ownerType={ownerType}
                                   busy={state.busy}
                                   onDefine={state.define}
                                   onPublish={state.publish}
                                   onDone={() => setEditing(null)}/>
            )}
        </section>
    );
};

export default ClockPoliciesPanel;
