import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {ReviewAssignmentChoice} from "../useInformationRequestReview.ts";
import {useReviewStagePickerStyles} from "./ReviewStagePickerStyles.tsx";

interface Props {
    choices: ReviewAssignmentChoice[];
    chosenId: string;
    busy: boolean;
    onChoose: (assignmentId: string) => void;
}

const choiceLabel = ({position, stageTitle, unsavedCount}: ReviewAssignmentChoice): string =>
    unsavedCount > 0 ? `${position}. ${stageTitle} (${unsavedCount} unsaved)` : `${position}. ${stageTitle}`;

const ReviewStagePicker = ({choices, chosenId, busy, onChoose}: Props) => {
    const styles = useReviewStagePickerStyles();

    return (
        <ChoiceSelect id={"information-request-review-stage-picker"}
                      label={"Stage you are deciding"}
                      hint={"You review more than one open stage. Unsaved choices stay with their stage."}
                      value={chosenId}
                      options={choices.map(choice => ({value: choice.assignmentId, label: choiceLabel(choice)}))}
                      disabled={busy}
                      className={styles.picker}
                      onChange={onChoose}/>
    );
};

export default ReviewStagePicker;
