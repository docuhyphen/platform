import {Divider, Text} from "@fluentui/react-components";
import {WorkflowStepSpecDraft, WorkflowTriggerEventDto} from "../../../../models/models.tsx";
import OutcomeField from "../outcome-field/OutcomeField.tsx";
import {useStepOutcomesSectionStyles} from "./StepOutcomesSectionStyles.tsx";

interface StepOutcomesSectionProps
{
    step: WorkflowStepSpecDraft;
    steps: WorkflowStepSpecDraft[];
    triggers: WorkflowTriggerEventDto[];
    onPatch: (patch: Partial<WorkflowStepSpecDraft>) => void;
}

const StepOutcomesSection = ({step, steps, triggers, onPatch}: StepOutcomesSectionProps) =>
{
    const styles = useStepOutcomesSectionStyles();

    return (
        <>
            {step.type === "WAIT_FOR_COUNTERPARTY_CLEARANCE" && (
                <>
                    <Divider id={"step-card-divider-2"}/>
                    <Text id={"step-card-text-7"}
                          size={200}
                          className={styles.waitDescription}>
                        Pauses this workflow until all workflows on the other party's side of this Exchange have completed.
                    </Text>
                    <div id={"step-card-div-13"}
                         className={styles.outcomeRow}>
                        <OutcomeField label="When unblocked"
                                      value={step.onApprove}
                                      steps={steps}
                                      triggers={triggers}
                                      onChange={v => onPatch({onApprove: v})}/>
                    </div>
                </>
            )}

            {(step.type === "APPROVAL" || step.type === "CONDITION") && <Divider id={"step-card-divider-3"}/>}

            {step.type === "APPROVAL" && (
                <div id={"step-card-div-14"}
                     className={styles.outcomeRow}>
                    <OutcomeField label="On Approve"
                                  value={step.onApprove}
                                  steps={steps}
                                  triggers={triggers}
                                  onChange={v => onPatch({onApprove: v})}/>
                    <OutcomeField label="On Reject"
                                  value={step.onReject}
                                  steps={steps}
                                  triggers={triggers}
                                  onChange={v => onPatch({onReject: v})}/>
                </div>
            )}

            {step.type === "CONDITION" && (
                <div id={"step-card-div-15"}
                     className={styles.outcomeRow}>
                    <OutcomeField label="If condition is true"
                                  value={step.onTrue}
                                  steps={steps}
                                  triggers={triggers}
                                  onChange={v => onPatch({onTrue: v})}/>
                    <OutcomeField label="If condition is false"
                                  value={step.onFalse}
                                  steps={steps}
                                  triggers={triggers}
                                  onChange={v => onPatch({onFalse: v})}/>
                </div>
            )}
        </>
    );
};

export default StepOutcomesSection;
