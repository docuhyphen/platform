import {Button, Text} from "@fluentui/react-components";
import {AddRegular} from "@fluentui/react-icons";
import {ClockPolicyPeriodForm, nextWorkingPeriod, workingPeriodProblems} from "../clock-policies/clockPolicyForm.ts";
import ClockPolicyWorkingPeriodRow from "../clock-policy-working-period-row/ClockPolicyWorkingPeriodRow.tsx";
import {useClockPolicyWorkingPeriodsStyles} from "./ClockPolicyWorkingPeriodsStyles.tsx";

interface ClockPolicyWorkingPeriodsProps {
    periods: ClockPolicyPeriodForm[];
    problem?: string;
    onChange: (periods: ClockPolicyPeriodForm[]) => void;
}

const ClockPolicyWorkingPeriods = ({periods, problem, onChange}: ClockPolicyWorkingPeriodsProps) => {
    const styles = useClockPolicyWorkingPeriodsStyles();
    const rowProblems = problem ? workingPeriodProblems(periods) : [];
    const replace = (index: number, period: ClockPolicyPeriodForm) =>
        onChange(periods.map((candidate, position) => position === index ? period : candidate));

    return (
        <div id={"information-request-clock-policy-periods"}
             role={"group"}
             aria-labelledby={"information-request-clock-policy-periods-label"}
             aria-describedby={"information-request-clock-policy-periods-hint"}
             className={styles.section}>
            <Text id={"information-request-clock-policy-periods-label"}
                  weight={"semibold"}>
                Working hours
            </Text>
            <Text id={"information-request-clock-policy-periods-hint"}
                  size={200}
                  className={styles.hint}>
                Add a period for each stretch of working time. A day can have several periods, and each day can have
                its own hours.
            </Text>
            <ul id={"information-request-clock-policy-period-list"}
                className={styles.list}>
                {periods.map((period, index) => (
                    <ClockPolicyWorkingPeriodRow key={index}
                                                 index={index}
                                                 period={period}
                                                 problems={rowProblems[index] ?? {}}
                                                 onChange={changed => replace(index, changed)}
                                                 onRemove={() => onChange(periods.filter((_, position) => position !== index))}/>
                ))}
            </ul>
            {problem && (
                <Text id={"information-request-clock-policy-periods-problem"}
                      role={"alert"}
                      className={styles.problem}>
                    {problem}
                </Text>
            )}
            <Button id={"information-request-clock-policy-period-add"}
                    appearance={"secondary"}
                    shape={"circular"}
                    icon={<AddRegular/>}
                    className={styles.add}
                    onClick={() => onChange([...periods, nextWorkingPeriod(periods)])}>
                Add working period
            </Button>
        </div>
    );
};

export default ClockPolicyWorkingPeriods;
