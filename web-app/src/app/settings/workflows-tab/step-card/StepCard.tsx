import {useState} from "react";
import {Divider} from "@fluentui/react-components";
import {
    AssigneeSpecDraft,
    WorkflowStepSpecDraft,
    WorkflowSubjectFieldDto,
    WorkflowTriggerEventDto,
} from "../../../models/models.tsx";
import {useStepCardStyles} from "./StepCardStyles.tsx";
import AssigneeBuilder from "../assignee-builder/AssigneeBuilder.tsx";
import CommunicationPickerDialog from "../../../components/communication-picker/CommunicationPickerDialog.tsx";
import ApprovalSlaFields from "./approval-sla-fields/ApprovalSlaFields.tsx";
import ExtractedConditionExpressionBuilder from "./condition-expression/ConditionExpressionBuilder.tsx";
import StepDeleteDialog from "./step-delete-dialog/StepDeleteDialog.tsx";
import StepCardHeader from "./step-card-header/StepCardHeader.tsx";
import StepIdentityFields from "./step-identity-fields/StepIdentityFields.tsx";
import StepQuorumField from "./step-quorum-field/StepQuorumField.tsx";
import StepActionField from "./step-action-field/StepActionField.tsx";
import StepOutcomesSection from "./step-outcomes-section/StepOutcomesSection.tsx";
import {useStepCommunication} from "./useStepCommunication.ts";
import {STEP_TYPE_LABELS} from "./stepCardOptions.ts";
import {findRoutesReferencingStep} from "../workflow-designer/stepMutations.ts";

interface Props
{
    index: number;
    step: WorkflowStepSpecDraft;
    steps: WorkflowStepSpecDraft[];
    onChange: (step: WorkflowStepSpecDraft) => void;
    onRemove: () => void;
    triggers: WorkflowTriggerEventDto[];
    subjectFields: WorkflowSubjectFieldDto[];
    onBack: () => void;
    platformMode?: boolean;
}

const StepCard = ({
    index,
    step,
    steps,
    onChange,
    onRemove,
    triggers,
    subjectFields,
    onBack,
    platformMode = false,
}: Props) =>
{
    const styles = useStepCardStyles();
    const [showDeleteDialog, setShowDeleteDialog] = useState(false);
    const patch = (p: Partial<WorkflowStepSpecDraft>) => onChange({...step, ...p});
    const patchAssignees = (assignees: AssigneeSpecDraft[]) => patch({assignees});
    const communication = useStepCommunication({step, platformMode, patch});
    const affectedRoutes = findRoutesReferencingStep(steps, index);

    const onDeleteConfirm = () =>
    {
        setShowDeleteDialog(false);
        onRemove();
    };

    return (
        <div id={"step-card-div-3"}
             className={styles.card}>
            <StepCardHeader index={index}
                            onBack={onBack}
                            onDelete={() => setShowDeleteDialog(true)} />

            <StepDeleteDialog open={showDeleteDialog}
                              stepNumber={index + 1}
                              stepLabel={step.name || STEP_TYPE_LABELS[step.type]}
                              affectedRoutes={affectedRoutes}
                              onConfirm={onDeleteConfirm}
                              onCancel={() => setShowDeleteDialog(false)} />

            <div id={"step-card-div-5"}
                 className={styles.cardBody}>
                <div id={"step-card-div-6"}
                     className={styles.fieldGroup}>
                    <StepIdentityFields index={index}
                                        step={step}
                                        onPatch={patch} />

                    {step.type === "APPROVAL" && (
                        <StepQuorumField index={index}
                                         step={step}
                                         onPatch={patch} />
                    )}

                    {(step.type === "NOTIFICATION" || step.type === "ACTION") && (
                        <StepActionField index={index}
                                         step={step}
                                         onPatch={patch}
                                         selectedCommunicationName={communication.selectedName}
                                         onPickCommunication={communication.openPicker}
                                         onClearCommunication={communication.clearCommunication} />
                    )}

                    {step.type === "CONDITION" && (
                        <ExtractedConditionExpressionBuilder expression={step.predicateExpression}
                                                             subjectFields={subjectFields}
                                                             onChange={expr => patch({predicateExpression: expr})}
                                                             allowTenantEntityLookup={!platformMode} />
                    )}

                    {step.type === "APPROVAL" && (
                        <ApprovalSlaFields index={index}
                                           step={step}
                                           subjectFields={subjectFields}
                                           onPatch={patch}
                                           allowTenantDirectory={!platformMode} />
                    )}
                </div>

                {(step.type === "APPROVAL" || step.type === "NOTIFICATION") && (
                    <>
                        <Divider id={"step-card-divider-1"}/>
                        <AssigneeBuilder label="Assignees"
                                         assignees={step.assignees}
                                         onChange={patchAssignees}
                                         subjectFields={subjectFields}
                                         allowTenantDirectory={!platformMode} />
                    </>
                )}

                <StepOutcomesSection step={step}
                                     steps={steps}
                                     triggers={triggers}
                                     onPatch={patch} />
            </div>

            <CommunicationPickerDialog open={communication.pickerOpen}
                                       onClose={communication.closePicker}
                                       onSelect={communication.selectCommunication}
                                       selectedId={step.communicationId}
                                       fixedScope={platformMode ? "PLATFORM" : undefined} />
        </div>
    );
};

export default StepCard;
