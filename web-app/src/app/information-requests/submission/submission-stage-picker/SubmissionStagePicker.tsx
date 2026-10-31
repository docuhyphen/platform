import {Dropdown, Field, Option} from "@fluentui/react-components";
import {InformationRequestSubmissionStageDto} from "../../../models/models.tsx";
import {humanizedKey, stageOptionLabel} from "../submissionLabels.ts";
import {useSubmissionStagePickerStyles} from "./SubmissionStagePickerStyles.tsx";

interface Props {
    stageKey: string;
    stages: InformationRequestSubmissionStageDto[];
    disabled: boolean;
    onSelect: (stageKey: string) => void;
}

const SubmissionStagePicker = ({stageKey, stages, disabled, onSelect}: Props) => {
    const styles = useSubmissionStagePickerStyles();

    return (
        <Field id={"information-request-submission-stage-field"}
               label={"Part to submit"}
               className={styles.stageField}>
            <Dropdown id={"information-request-submission-stage"}
                      value={humanizedKey(stageKey)}
                      selectedOptions={[stageKey]}
                      disabled={disabled}
                      onOptionSelect={(_, data) => data.optionValue && onSelect(data.optionValue)}>
                {stages.map(stage => (
                    <Option id={`information-request-submission-stage-${stage.stageKey}`}
                            key={stage.stageKey}
                            value={stage.stageKey}
                            text={humanizedKey(stage.stageKey)}>
                        {stageOptionLabel(stage)}
                    </Option>
                ))}
            </Dropdown>
        </Field>
    );
};

export default SubmissionStagePicker;
