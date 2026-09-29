import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {InformationRequestBusinessDecisionDto, InformationRequestBusinessDecisionKind} from "../../../models/models.tsx";
import BusinessDecisionRow from "../business-decision-row/BusinessDecisionRow.tsx";
import RecordDecisionDialog from "../record-decision-dialog/RecordDecisionDialog.tsx";
import {latestDecisionIds} from "../request-outcomes-panel/outcomeLabels.ts";
import {RequestOutcomes} from "../request-outcomes-panel/useRequestOutcomes.ts";
import {useBusinessDecisionsSectionStyles} from "./BusinessDecisionsSectionStyles.tsx";

interface Props
{
    outcomes: RequestOutcomes;
    editable: boolean;
}

interface PendingDecision
{
    kind: InformationRequestBusinessDecisionKind;
    prior?: InformationRequestBusinessDecisionDto;
}

const BusinessDecisionsSection = ({outcomes, editable}: Props) =>
{
    const styles = useBusinessDecisionsSectionStyles();
    const [pending, setPending] = useState<PendingDecision | null>(null);
    const latest = latestDecisionIds(outcomes.decisions);
    const ordered = [...outcomes.decisions].sort((first, second) =>
        first.owningProcessKey.localeCompare(second.owningProcessKey) || first.decisionRevision - second.decisionRevision);

    return (
        <section id={"information-request-business-decisions"}
                 aria-labelledby={"information-request-business-decisions-title"}
                 className={styles.section}>
            <div id={"information-request-business-decisions-header"}
                 className={styles.header}>
                <Text id={"information-request-business-decisions-title"}
                      as={"h3"}
                      size={400}
                      weight={"semibold"}
                      className={styles.heading}>
                    Business decisions
                </Text>
                {editable && (
                    <Button id={"information-request-business-decisions-record"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={outcomes.busy}
                            onClick={() => setPending({kind: InformationRequestBusinessDecisionKind.ORIGINAL})}>
                        Record decision
                    </Button>
                )}
            </div>
            <Text id={"information-request-business-decisions-explanation"}
                  className={styles.muted}>
                A business decision records what your own process decided about this request. A later decision on
                the same process reconsiders or appeals the latest one; earlier decisions stay in the history.
            </Text>
            {ordered.length === 0 && (
                <Text id={"information-request-business-decisions-empty"}>
                    No business decision has been recorded.
                </Text>
            )}
            {ordered.length > 0 && (
                <ul id={"information-request-business-decisions-list"}
                    aria-labelledby={"information-request-business-decisions-title"}
                    className={styles.list}>
                    {ordered.map(decision => (
                        <BusinessDecisionRow key={decision.id}
                                             decision={decision}
                                             canFollowUp={editable && latest.has(decision.id)}
                                             busy={outcomes.busy}
                                             onFollowUp={kind => setPending({kind, prior: decision})}/>
                    ))}
                </ul>
            )}
            {pending && (
                <RecordDecisionDialog kind={pending.kind}
                                      prior={pending.prior}
                                      busy={outcomes.busy}
                                      onConfirm={request =>
                                      {
                                          setPending(null);
                                          void outcomes.record(request);
                                      }}
                                      onDismiss={() => setPending(null)}/>
            )}
        </section>
    );
};

export default BusinessDecisionsSection;
