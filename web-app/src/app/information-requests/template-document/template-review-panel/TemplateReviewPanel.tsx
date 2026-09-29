import {Text} from "@fluentui/react-components";
import {
    InformationRequestReviewAggregation,
    InformationRequestReviewStageOrdering,
    InformationRequestTemplateReviewStageRequest,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {aggregationLabels, reviewStageOrderingLabels} from "../templateAuthoringLabels.ts";
import {moveItem, removeItem} from "../templateDraftDocument.ts";
import TemplateKeyedList from "../template-keyed-list/TemplateKeyedList.tsx";
import TemplateReviewStageDialog from "../template-review-stage-dialog/TemplateReviewStageDialog.tsx";
import {problemsForItem, useFocusedItem} from "../useFocusedItem.ts";
import {useTemplateReviewPanelStyles} from "./TemplateReviewPanelStyles.tsx";

const describe = (stage: InformationRequestTemplateReviewStageRequest): string =>
{
    const coverage = (stage.sectionKeys ?? []).length === 0 ? "every section" : (stage.sectionKeys ?? []).join(", ");
    return `${aggregationLabels[stage.aggregation ?? InformationRequestReviewAggregation.ANY]}, `
        + `at least ${stage.minimumReviewerCount ?? 1} reviewers, covers ${coverage}`;
};

const TemplateReviewPanel = () =>
{
    const styles = useTemplateReviewPanelStyles();
    const {document, readOnly, problems, update} = useTemplateDocument();
    const {editing, setEditing, highlighted} = useFocusedItem("review");
    const planProblems = problems
        .filter(problem => problem.target.panel === "review" && problem.target.stageIndex === undefined);

    return (
        <div id={"template-review-panel"}
             className={styles.panel}>
            <ChoiceSelect id={"template-review-ordering-select"}
                          label={"Stage order"}
                          value={document.reviewStageOrdering}
                          options={optionsFrom(reviewStageOrderingLabels)}
                          disabled={readOnly}
                          className={styles.ordering}
                          onChange={reviewStageOrdering => update(current => ({...current, reviewStageOrdering}))}/>
            {document.reviewStageOrdering === InformationRequestReviewStageOrdering.SEQUENTIAL && (
                <Text id={"template-review-ordering-hint"}
                      className={styles.muted}>
                    Stages open in the order listed; each waits for the one before it to settle.
                </Text>
            )}
            {planProblems.map(problem => (
                <Text key={problem.key}
                      id={`template-review-${problem.key}`}
                      className={styles.problem}>
                    {problem.message}
                </Text>
            ))}
            <TemplateKeyedList id={"template-review-stages"}
                               noun={"review stage"}
                               items={document.reviewStages.map((stage, index) => ({
                                   name: stage.title.trim() || stage.stageKey,
                                   detail: describe(stage),
                                   problems: problemsForItem(problems, "review", index),
                               }))}
                               readOnly={readOnly}
                               emptyText={"No review stages. When a requirement is reviewed, one default stage covers every reviewed requirement."}
                               addLabel={"Add review stage"}
                               movable={true}
                               highlightedIndex={highlighted}
                               onAdd={() => setEditing("new")}
                               onEdit={index => setEditing(index)}
                               onMove={(index, offset) => update(current => ({
                                   ...current,
                                   reviewStages: moveItem(current.reviewStages, index, offset),
                               }))}
                               onRemove={index => update(current => ({
                                   ...current,
                                   reviewStages: removeItem(current.reviewStages, index),
                               }))}/>
            {editing !== null && (
                <TemplateReviewStageDialog stageIndex={editing === "new" ? undefined : editing}
                                           onClose={() => setEditing(null)}/>
            )}
        </div>
    );
};

export default TemplateReviewPanel;
