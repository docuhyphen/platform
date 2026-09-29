import {useState} from "react";
import {InformationRequestReviewVisibility} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import EditorDialog from "../../shared/editor-dialog/EditorDialog.tsx";
import TextField from "../../shared/text-field/TextField.tsx";
import {visibilityLabels} from "../reviewLabels.ts";

interface Props
{
    label: string;
    busy: boolean;
    onConfirm: (body: string, visibility: InformationRequestReviewVisibility) => void;
    onDismiss: () => void;
}

const ReviewCommentDialog = ({label, busy, onConfirm, onDismiss}: Props) =>
{
    const [body, setBody] = useState("");
    const [visibility, setVisibility] = useState(InformationRequestReviewVisibility.RESPONDENT_VISIBLE);

    return (
        <EditorDialog id={"information-request-review-comment"}
                      title={`Comment on ${label}`}
                      confirmLabel={"Send comment"}
                      busy={busy}
                      confirmDisabled={!body.trim()}
                      onConfirm={() => onConfirm(body.trim(), visibility)}
                      onDismiss={onDismiss}>
            <TextField id={"information-request-review-comment-body"}
                       label={"Comment"}
                       multiline={true}
                       maxLength={4000}
                       value={body}
                       onChange={setBody}/>
            <ChoiceSelect<InformationRequestReviewVisibility> id={"information-request-review-comment-visibility"}
                                                              label={"Who can see it"}
                                                              value={visibility}
                                                              options={optionsFrom<InformationRequestReviewVisibility>(visibilityLabels)}
                                                              onChange={setVisibility}/>
        </EditorDialog>
    );
};

export default ReviewCommentDialog;
