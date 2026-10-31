import {Button, Checkbox, Field, Input} from "@fluentui/react-components";
import {DeleteRegular} from "@fluentui/react-icons";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import {ClockPolicyPeriodForm, ClockPolicyPeriodProblems} from "../clock-policies/clockPolicyForm.ts";
import {dayLabels} from "../clock-policies/clockPolicyLabels.ts";
import {useClockPolicyWorkingPeriodRowStyles} from "./ClockPolicyWorkingPeriodRowStyles.tsx";

interface ClockPolicyWorkingPeriodRowProps {
    index: number;
    period: ClockPolicyPeriodForm;
    problems: ClockPolicyPeriodProblems;
    onChange: (period: ClockPolicyPeriodForm) => void;
    onRemove: () => void;
}

const dayOptions = optionsFrom<string>(dayLabels);

const ClockPolicyWorkingPeriodRow = ({
                                         index,
                                         period,
                                         problems,
                                         onChange,
                                         onRemove
                                     }: ClockPolicyWorkingPeriodRowProps) => {
    const styles = useClockPolicyWorkingPeriodRowStyles();
    const id = `information-request-clock-policy-period-${index}`;

    return (
        <li id={id}
            className={styles.item}>
            <div id={`${id}-group`}
                 role={"group"}
                 aria-label={`Working period ${index + 1}`}
                 className={styles.row}>
                <ChoiceSelect<string> id={`${id}-day`}
                                      label={"Day"}
                                      value={period.dayOfWeek}
                                      options={dayOptions}
                                      className={styles.day}
                                      onChange={dayOfWeek => onChange({...period, dayOfWeek})}/>
                <Field id={`${id}-start-field`}
                       label={"Starts"}
                       validationState={problems.start ? "error" : "none"}
                       validationMessage={problems.start}>
                    <Input id={`${id}-start`}
                           type={"time"}
                           value={period.start}
                           onChange={(_, data) => onChange({...period, start: data.value})}/>
                </Field>
                <div id={`${id}-end-cell`}
                     className={styles.end}>
                    <Field id={`${id}-end-field`}
                           label={"Ends"}
                           validationState={problems.end ? "error" : "none"}
                           validationMessage={problems.end}>
                        <Input id={`${id}-end`}
                               type={"time"}
                               value={period.untilMidnight ? "" : period.end}
                               disabled={period.untilMidnight}
                               onChange={(_, data) => onChange({...period, end: data.value})}/>
                    </Field>
                    <Checkbox id={`${id}-until-midnight`}
                              label={"Until midnight"}
                              checked={period.untilMidnight}
                              onChange={(_, data) => onChange({...period, untilMidnight: data.checked === true})}/>
                </div>
                <Button id={`${id}-remove`}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={<DeleteRegular/>}
                        aria-label={`Remove working period ${index + 1}`}
                        className={styles.remove}
                        onClick={onRemove}/>
            </div>
        </li>
    );
};

export default ClockPolicyWorkingPeriodRow;
