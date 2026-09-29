import {InformationRequestClockDueEffect} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {ClockPolicyForm, ClockPolicyProblems} from "../clock-policies/clockPolicyForm.ts";
import {dueEffectLabels} from "../clock-policies/clockPolicyLabels.ts";
import {useClockPolicyTimingFieldsStyles} from "./ClockPolicyTimingFieldsStyles.tsx";

interface ClockPolicyTimingFieldsProps
{
    form: ClockPolicyForm;
    problems: ClockPolicyProblems;
    onChange: <K extends keyof ClockPolicyForm>(field: K, value: ClockPolicyForm[K]) => void;
}

const ClockPolicyTimingFields = ({form, problems, onChange}: ClockPolicyTimingFieldsProps) =>
{
    const styles = useClockPolicyTimingFieldsStyles();

    return (
        <>
            <div id={"information-request-clock-policy-durations"}
                 className={styles.durations}>
                <TextField id={"information-request-clock-policy-standard"}
                           label={"Standard hours"}
                           hint={"How long a request normally has."}
                           required={true}
                           value={form.standardHours}
                           validationMessage={problems.standardHours}
                           onChange={value => onChange("standardHours", value)}/>
                <TextField id={"information-request-clock-policy-urgent"}
                           label={"Urgent hours"}
                           hint={"How long an urgent request has."}
                           required={true}
                           value={form.urgentHours}
                           validationMessage={problems.urgentHours}
                           onChange={value => onChange("urgentHours", value)}/>
            </div>
            <TextField id={"information-request-clock-policy-reminders"}
                       label={"Reminders"}
                       hint={"Hours before the due time, separated by commas, such as 24, 4."}
                       value={form.reminderHours}
                       validationMessage={problems.reminderHours}
                       onChange={value => onChange("reminderHours", value)}/>
            <TextField id={"information-request-clock-policy-escalation"}
                       label={"Escalate after"}
                       hint={"Hours after the due time. Leave empty to never escalate."}
                       value={form.escalationHours}
                       validationMessage={problems.escalationHours}
                       onChange={value => onChange("escalationHours", value)}/>
            <ChoiceSelect<InformationRequestClockDueEffect> id={"information-request-clock-policy-due-effect"}
                                                            label={"When due"}
                                                            value={form.dueEffect}
                                                            options={optionsFrom<InformationRequestClockDueEffect>(dueEffectLabels)}
                                                            onChange={value => onChange("dueEffect", value)}/>
        </>
    );
};

export default ClockPolicyTimingFields;
