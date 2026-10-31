import {Spinner, Text} from "@fluentui/react-components";
import ChoiceSelect from "../../../../information-requests/shared/choice-select/ChoiceSelect.tsx";
import InformationRequestLoadError
    from "../../../../information-requests/shared/load-error/InformationRequestLoadError.tsx";
import {WorkflowApplicabilityDraft, WorkflowRequirementConditionDraft} from "../../../../models/models.tsx";
import RequirementConditionRow from "./requirement-condition-row/RequirementConditionRow.tsx";
import {defaultRequirementCondition} from "./requirementConditionChoices.ts";
import {useRequirementConditionSources} from "./useRequirementConditionSources.ts";
import {useRequirementConditionEditorStyles} from "./RequirementConditionEditorStyles.tsx";

interface RequirementConditionEditorProps {
    applicability?: WorkflowApplicabilityDraft;
    onChange: (next?: WorkflowApplicabilityDraft) => void;
}

const keyOf = (templateRequirementId: string, occurrencePath: string): string => `${templateRequirementId}@${occurrencePath}`;

const RequirementConditionEditor = ({applicability, onChange}: RequirementConditionEditorProps) => {
    const styles = useRequirementConditionEditorStyles();
    const conditions = applicability?.requirementConditions ?? [];
    const sources = useRequirementConditionSources(conditions.map(condition => condition.templateRequirementId));
    const used = new Set(conditions.map(condition => keyOf(condition.templateRequirementId, condition.occurrencePath)));
    const available = sources.choices.filter(choice => !used.has(keyOf(choice.templateRequirementId, choice.occurrencePath)));

    const emit = (next: WorkflowRequirementConditionDraft[]) =>
        onChange(next.length > 0 ? {fieldConditions: [], requirementConditions: next} : undefined);

    const add = (templateRequirementId: string) => {
        const choice = available.find(candidate => candidate.templateRequirementId === templateRequirementId);
        if (choice) emit([...conditions, defaultRequirementCondition(choice)]);
    };

    return (
        <div id={"requirement-condition-editor"}
             className={styles.editor}>
            <Text id={"requirement-condition-editor-hint"}
                  size={200}
                  className={styles.hint}>
                The workflow runs only for Submission Packages whose answers meet every condition (AND). Leave it empty
                to
                run for every event of the trigger. Exchange Field conditions apply only to Exchange triggers.
            </Text>
            <ChoiceSelect id={"requirement-condition-template"}
                          label={"Template"}
                          value={sources.templateId}
                          placeholder={"Choose a Template"}
                          hint={"Typed answers of the organization Template's latest published Version can be compared."}
                          options={sources.templates.map(template => ({
                              value: template.id,
                              label: template.displayName
                          }))}
                          className={styles.picker}
                          onChange={sources.setTemplateId}/>
            {sources.templatesError && (
                <InformationRequestLoadError id={"requirement-condition-templates-error"}
                                             message={sources.templatesError}
                                             onRetry={() => void sources.reloadTemplates()}/>
            )}
            <div id={"requirement-condition-list"}
                 className={styles.list}>
                {conditions.map((condition, index) => (
                    <RequirementConditionRow key={`${condition.templateRequirementId}-${index}`}
                                             index={index}
                                             condition={condition}
                                             choice={sources.choices.find(choice => choice.templateRequirementId === condition.templateRequirementId)}
                                             onChange={next => emit(conditions.map((current, position) => position === index ? next : current))}
                                             onRemove={() => emit(conditions.filter((_, position) => position !== index))}/>
                ))}
                {conditions.length === 0 && (
                    <Text id={"requirement-condition-empty"}
                          size={200}
                          className={styles.empty}>
                        No requirement conditions. This workflow runs for every event of its trigger.
                    </Text>
                )}
            </div>
            {sources.loading && (
                <Spinner id={"requirement-condition-loading"}
                         size={"tiny"}
                         label={"Loading the Template's answers"}/>
            )}
            {sources.answersError && (
                <InformationRequestLoadError id={"requirement-condition-answers-error"}
                                             message={sources.answersError}
                                             onRetry={() => void sources.reloadAnswers()}/>
            )}
            {sources.templateId && !sources.loading && !sources.answersError && (
                <ChoiceSelect id={"requirement-condition-add"}
                              label={"Add condition"}
                              value={""}
                              placeholder={available.length === 0 ? "No typed answer left to add" : "Choose a typed answer"}
                              options={available.map(choice => ({
                                  value: choice.templateRequirementId,
                                  label: `${choice.prompt} (${choice.requirementKey})`,
                              }))}
                              disabled={available.length === 0}
                              className={styles.picker}
                              onChange={add}/>
            )}
        </div>
    );
};

export default RequirementConditionEditor;
