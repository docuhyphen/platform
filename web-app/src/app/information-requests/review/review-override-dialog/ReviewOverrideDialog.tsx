import {useState} from "react";
import {InformationRequestReviewOutcome} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {reviewOutcomeLabels} from "../reviewLabels.ts";

interface Props
{
    busy: boolean;
    onConfirm: (outcome: InformationRequestReviewOutcome, narrative: string) => void;
    onDismiss: () => void;
}

const ReviewOverrideDialog = ({busy, onConfirm, onDismiss}: Props) =>
{
    const [outcome, setOutcome] = useState<InformationRequestReviewOutcome | "">("");
    const [narrative, setNarrative] = useState("");

    return (
        <EditorDialog id={"information-request-review-override"}
                      title={"Override the decision"}
                      confirmLabel={"Override"}
                      busy={busy}
                      confirmDisabled={!outcome || !narrative.trim()}
                      onConfirm={() => outcome && onConfirm(outcome, narrative.trim())}
                      onDismiss={onDismiss}>
            <ChoiceSelect<InformationRequestReviewOutcome> id={"information-request-review-override-outcome"}
                                                           label={"Outcome"}
                                                           value={outcome}
                                                           placeholder={"Choose the outcome"}
                                                           options={optionsFrom<InformationRequestReviewOutcome>(reviewOutcomeLabels)}
                                                           onChange={setOutcome}/>
            <TextField id={"information-request-review-override-narrative"}
                       label={"Why"}
                       hint={"The override and its reason are recorded in the review history."}
                       multiline={true}
                       maxLength={4000}
                       value={narrative}
                       onChange={setNarrative}/>
        </EditorDialog>
    );
};

export default ReviewOverrideDialog;
