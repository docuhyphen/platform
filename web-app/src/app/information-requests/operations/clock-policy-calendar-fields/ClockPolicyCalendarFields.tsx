import {Checkbox, Field, Input} from "@fluentui/react-components";
import {InformationRequestClockType} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {ClockPolicyForm, ClockPolicyProblems, WEEK_DAYS} from "../clock-policies/clockPolicyForm.ts";
import {clockTypeLabels, dayLabels} from "../clock-policies/clockPolicyLabels.ts";
import {useClockPolicyCalendarFieldsStyles} from "./ClockPolicyCalendarFieldsStyles.tsx";

interface ClockPolicyCalendarFieldsProps
{
    form: ClockPolicyForm;
    problems: ClockPolicyProblems;
    onChange: <K extends keyof ClockPolicyForm>(field: K, value: ClockPolicyForm[K]) => void;
}

const ClockPolicyCalendarFields = ({form, problems, onChange}: ClockPolicyCalendarFieldsProps) =>
{
    const styles = useClockPolicyCalendarFieldsStyles();
    const business = form.clockType === InformationRequestClockType.BUSINESS;
    const toggleDay = (day: string, checked: boolean) =>
        onChange("workingDays", checked ? [...form.workingDays, day] : form.workingDays.filter(candidate => candidate !== day));

    return (
        <>
            <ChoiceSelect<InformationRequestClockType> id={"information-request-clock-policy-type"}
                                                       label={"Clock"}
                                                       hint={"Business hours count only working time."}
                                                       value={form.clockType}
                                                       options={optionsFrom<InformationRequestClockType>(clockTypeLabels)}
                                                       onChange={value => onChange("clockType", value)}/>
            <TextField id={"information-request-clock-policy-zone"}
                       label={"Time zone"}
                       hint={"A time zone name such as Europe/London."}
                       value={form.businessTimezone}
                       validationMessage={problems.businessTimezone}
                       onChange={value => onChange("businessTimezone", value)}/>
            {business && (
                <>
                    <Field id={"information-request-clock-policy-days-field"}
                           label={"Working days"}
                           validationState={problems.workingDays ? "error" : "none"}
                           validationMessage={problems.workingDays}>
                        <div id={"information-request-clock-policy-days"}
                             role={"group"}
                             aria-label={"Working days"}
                             className={styles.days}>
                            {WEEK_DAYS.map(day => (
                                <Checkbox key={day}
                                          id={`information-request-clock-policy-day-${day.toLowerCase()}`}
                                          label={dayLabels[day]}
                                          checked={form.workingDays.includes(day)}
                                          onChange={(_, data) => toggleDay(day, data.checked === true)}/>
                            ))}
                        </div>
                    </Field>
                    <div id={"information-request-clock-policy-hours"}
                         className={styles.hours}>
                        <Field id={"information-request-clock-policy-start-field"}
                               label={"Workday starts"}
                               validationState={problems.workdayStart ? "error" : "none"}
                               validationMessage={problems.workdayStart}>
                            <Input id={"information-request-clock-policy-start"}
                                   type={"time"}
                                   value={form.workdayStart}
                                   onChange={(_, data) => onChange("workdayStart", data.value)}/>
                        </Field>
                        <Field id={"information-request-clock-policy-end-field"}
                               label={"Workday ends"}
                               validationState={problems.workdayEnd ? "error" : "none"}
                               validationMessage={problems.workdayEnd}>
                            <Input id={"information-request-clock-policy-end"}
                                   type={"time"}
                                   value={form.workdayEnd}
                                   onChange={(_, data) => onChange("workdayEnd", data.value)}/>
                        </Field>
                    </div>
                    <TextField id={"information-request-clock-policy-holidays"}
                               label={"Holidays"}
                               hint={"Days the clock skips, such as 2026-12-25, separated by commas."}
                               value={form.holidays}
                               validationMessage={problems.holidays}
                               onChange={value => onChange("holidays", value)}/>
                </>
            )}
        </>
    );
};

export default ClockPolicyCalendarFields;
