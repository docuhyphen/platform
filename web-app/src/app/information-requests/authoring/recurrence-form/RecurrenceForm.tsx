import {useState} from "react";
import {Button, Field, Input} from "@fluentui/react-components";
import {DefineInformationRequestRecurrenceRequest, InformationRequestRecurrenceUnit} from "../../../models/models.tsx";
import ChoiceSelect from "../../shared/choice-select/ChoiceSelect.tsx";
import {optionsFrom} from "../../shared/choice-select/choiceOptions.ts";
import NumberField from "../../shared/number-field/NumberField.tsx";
import {recurrenceUnitLabels} from "../follow-up-panel/followUpLabels.ts";
import {useRecurrenceFormStyles} from "./RecurrenceFormStyles.tsx";

interface Props
{
    busy: boolean;
    onSchedule: (request: DefineInformationRequestRecurrenceRequest) => void;
}

const RecurrenceForm = ({busy, onSchedule}: Props) =>
{
    const styles = useRecurrenceFormStyles();
    const [count, setCount] = useState<number | undefined>(1);
    const [unit, setUnit] = useState(InformationRequestRecurrenceUnit.MONTH);
    const [firstDue, setFirstDue] = useState("");
    const [maximum, setMaximum] = useState<number | undefined>(undefined);
    const firstDueTime = firstDue ? new Date(firstDue) : null;
    const ready = Boolean(count && count > 0 && firstDueTime && !Number.isNaN(firstDueTime.getTime()) && (maximum === undefined || maximum > 0));

    const schedule = () =>
    {
        if (!ready || !count || !firstDueTime) return;
        onSchedule({
            intervalUnit: unit,
            intervalCount: count,
            firstDueAt: firstDueTime.toISOString(),
            ...(maximum ? {maximumOccurrences: maximum} : {}),
        });
    };

    return (
        <div id={"information-request-recurrence-form"}
             role={"group"}
             aria-label={"Schedule a recurrence"}
             className={styles.form}>
            <NumberField id={"information-request-recurrence-count"}
                         label={"Repeat every"}
                         value={count}
                         min={1}
                         step={1}
                         disabled={busy}
                         onChange={setCount}/>
            <ChoiceSelect<InformationRequestRecurrenceUnit> id={"information-request-recurrence-unit"}
                                                            label={"Unit"}
                                                            value={unit}
                                                            disabled={busy}
                                                            options={optionsFrom<InformationRequestRecurrenceUnit>(recurrenceUnitLabels)}
                                                            onChange={setUnit}/>
            <Field id={"information-request-recurrence-first-due-field"}
                   label={"First due"}>
                <Input id={"information-request-recurrence-first-due"}
                       type={"datetime-local"}
                       value={firstDue}
                       disabled={busy}
                       onChange={(_, data) => setFirstDue(data.value)}/>
            </Field>
            <NumberField id={"information-request-recurrence-maximum"}
                         label={"Stop after"}
                         hint={"Requests in all. Leave empty to keep repeating."}
                         value={maximum}
                         min={1}
                         step={1}
                         disabled={busy}
                         onChange={setMaximum}/>
            <Button id={"information-request-recurrence-schedule"}
                    appearance={"secondary"}
                    shape={"circular"}
                    className={styles.submit}
                    disabled={busy || !ready}
                    onClick={schedule}>
                Schedule
            </Button>
        </div>
    );
};

export default RecurrenceForm;
