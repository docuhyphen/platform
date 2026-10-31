import {InformationRequestClockType} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import TextField from "../../shared/text-field/TextField.tsx";
import {ClockPolicyForm, ClockPolicyProblems} from "../clock-policies/clockPolicyForm.ts";
import {clockTypeLabels} from "../clock-policies/clockPolicyLabels.ts";
import ClockPolicyWorkingPeriods from "../clock-policy-working-periods/ClockPolicyWorkingPeriods.tsx";

interface ClockPolicyCalendarFieldsProps
{
    form: ClockPolicyForm;
    problems: ClockPolicyProblems;
    onChange: <K extends keyof ClockPolicyForm>(field: K, value: ClockPolicyForm[K]) => void;
}

const ClockPolicyCalendarFields = ({form, problems, onChange}: ClockPolicyCalendarFieldsProps) => (
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
        {form.clockType === InformationRequestClockType.BUSINESS && (
            <>
                <ClockPolicyWorkingPeriods periods={form.workingPeriods}
                                           problem={problems.workingPeriods}
                                           onChange={periods => onChange("workingPeriods", periods)}/>
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

export default ClockPolicyCalendarFields;
