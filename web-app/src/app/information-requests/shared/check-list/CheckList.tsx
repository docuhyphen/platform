import {Checkbox, Field, FieldContextProvider} from "@fluentui/react-components";
import {ChoiceOption} from "../choice-select/choiceOptions.ts";
import {useCheckListStyles} from "./CheckListStyles.tsx";

interface CheckListProps<T extends string>
{
    id: string;
    label: string;
    options: ChoiceOption<T>[];
    selected: T[];
    disabled?: boolean;
    hint?: string;
    onChange: (selected: T[]) => void;
}

const CheckList = <T extends string>({id, label, options, selected, disabled, hint, onChange}: CheckListProps<T>) =>
{
    const styles = useCheckListStyles();
    const toggle = (value: T, checked: boolean) =>
        onChange(checked
            ? options.map(option => option.value).filter(option => option === value || selected.includes(option))
            : selected.filter(existing => existing !== value));

    return (
        <Field id={`${id}-field`}
               label={label}
               hint={hint}>
            <div id={id}
                 role={"group"}
                 aria-label={label}
                 className={styles.options}>
                <FieldContextProvider value={undefined}>
                    {options.map(option => (
                        <Checkbox key={option.value}
                                  id={`${id}-${option.value}`}
                                  label={option.label}
                                  checked={selected.includes(option.value)}
                                  disabled={disabled}
                                  onChange={(_, data) => toggle(option.value, data.checked === true)}/>
                    ))}
                </FieldContextProvider>
            </div>
        </Field>
    );
};

export default CheckList;
