import {useState} from "react";
import {Checkbox} from "@fluentui/react-components";
import {
    InformationRequestReviewAggregation,
    InformationRequestReviewTieResolution,
    InformationRequestTemplateReviewStageRequest,
} from "../../../models/models.tsx";
import CheckList from "../../shared/check-list/CheckList.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import NumberField from "../../shared/number-field/NumberField.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {aggregationLabels, tieResolutionLabels} from "../templateAuthoringLabels.ts";
import {keyFromLabel, replaceItem} from "../templateDraftDocument.ts";

interface TemplateReviewStageDialogProps
{
    stageIndex?: number;
    onClose: () => void;
}

const emptyStage = (): InformationRequestTemplateReviewStageRequest => ({
    stageKey: "",
    title: "",
    aggregation: InformationRequestReviewAggregation.ANY,
    minimumReviewerCount: 1,
    tieResolution: InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
    overridePermitted: false,
    excludesResponseParties: false,
    excludesPriorReviewers: false,
    sectionKeys: [],
});

const TemplateReviewStageDialog = ({stageIndex, onClose}: TemplateReviewStageDialogProps) =>
{
    const {document, readOnly, update} = useTemplateDocument();
    const existing = stageIndex === undefined ? undefined : document.reviewStages[stageIndex];
    const [stage, setStage] = useState<InformationRequestTemplateReviewStageRequest>(existing ?? emptyStage());
    const [keyEdited, setKeyEdited] = useState(existing !== undefined);
    const set = (change: Partial<InformationRequestTemplateReviewStageRequest>) => setStage(current => ({...current, ...change}));
    const byQuorum = stage.aggregation === InformationRequestReviewAggregation.QUORUM;

    const save = () =>
    {
        const saved = {
            ...stage,
            stageKey: stage.stageKey.trim(),
            title: stage.title.trim(),
            quorumCount: byQuorum ? stage.quorumCount : undefined,
        };
        update(current => ({
            ...current,
            reviewStages: stageIndex === undefined
                ? [...current.reviewStages, saved]
                : replaceItem(current.reviewStages, stageIndex, saved),
        }));
        onClose();
    };

    return (
        <EditorDialog id={"template-review-stage-dialog"}
                      title={readOnly ? "Review stage" : existing ? "Edit review stage" : "Add review stage"}
                      readOnly={readOnly}
                      confirmDisabled={!stage.title.trim() || !stage.stageKey.trim()}
                      onConfirm={save}
                      onDismiss={onClose}>
            <TextField id={"template-review-stage-title-input"}
                       label={"Title"}
                       value={stage.title}
                       disabled={readOnly}
                       onChange={title => set(keyEdited ? {title} : {title, stageKey: keyFromLabel(title)})}/>
            <TextField id={"template-review-stage-key-input"}
                       label={"Key"}
                       value={stage.stageKey}
                       disabled={readOnly}
                       onChange={stageKey =>
                       {
                           setKeyEdited(true);
                           set({stageKey});
                       }}/>
            <ChoiceSelect id={"template-review-stage-aggregation-select"}
                          label={"Decision rule"}
                          value={stage.aggregation ?? InformationRequestReviewAggregation.ANY}
                          options={optionsFrom(aggregationLabels)}
                          disabled={readOnly}
                          onChange={aggregation => set({aggregation})}/>
            {byQuorum && (
                <NumberField id={"template-review-stage-quorum-input"}
                             label={"Quorum"}
                             value={stage.quorumCount}
                             min={1}
                             disabled={readOnly}
                             onChange={quorumCount => set({quorumCount})}/>
            )}
            <NumberField id={"template-review-stage-reviewers-input"}
                         label={"Minimum reviewers"}
                         value={stage.minimumReviewerCount}
                         min={1}
                         disabled={readOnly}
                         onChange={minimumReviewerCount => set({minimumReviewerCount})}/>
            <ChoiceSelect id={"template-review-stage-tie-select"}
                          label={"When reviewers tie"}
                          value={stage.tieResolution ?? InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME}
                          options={optionsFrom(tieResolutionLabels)}
                          disabled={readOnly}
                          onChange={tieResolution => set({tieResolution})}/>
            <Checkbox id={"template-review-stage-override-checkbox"}
                      label={"Allow an authorized override"}
                      checked={stage.overridePermitted ?? false}
                      disabled={readOnly}
                      onChange={(_, data) => set({overridePermitted: data.checked === true})}/>
            <Checkbox id={"template-review-stage-exclude-parties-checkbox"}
                      label={"Responding parties may not review"}
                      checked={stage.excludesResponseParties ?? false}
                      disabled={readOnly}
                      onChange={(_, data) => set({excludesResponseParties: data.checked === true})}/>
            <Checkbox id={"template-review-stage-exclude-prior-checkbox"}
                      label={"Reviewers of an earlier stage may not review"}
                      checked={stage.excludesPriorReviewers ?? false}
                      disabled={readOnly}
                      onChange={(_, data) => set({excludesPriorReviewers: data.checked === true})}/>
            <CheckList id={"template-review-stage-sections"}
                       label={"Sections covered"}
                       hint={"Leave every section unticked to cover them all."}
                       options={document.sections.map(section => ({
                           value: section.sectionKey,
                           label: section.title.trim() || section.sectionKey,
                       }))}
                       selected={stage.sectionKeys ?? []}
                       disabled={readOnly}
                       onChange={sectionKeys => set({sectionKeys})}/>
        </EditorDialog>
    );
};

export default TemplateReviewStageDialog;
