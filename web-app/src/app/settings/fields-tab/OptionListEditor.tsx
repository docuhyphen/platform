import {Button, Field, Input} from '@fluentui/react-components';
import {DeleteRegular} from '@fluentui/react-icons';
import {AddIcon} from '../../components/IconBundles';
import {FieldOption} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';

interface Props
{
    options: FieldOption[];
    onChange: (options: FieldOption[]) => void;
}

const OptionListEditor = ({options, onChange}: Props) =>
{
    const styles = useFieldsTabStyles();

    const update = (index: number, patch: Partial<FieldOption>) =>
        onChange(options.map((option, i) => (i === index ? {...option, ...patch} : option)));

    const remove = (index: number) =>
        onChange(options.filter((_, i) => i !== index));

    const add = () =>
        onChange([...options, {code: '', label: '', order: options.length, active: true}]);

    return (
        <div className={styles.fieldGroup}>
            {options.map((option, index) => (
                <div key={index}
                     className={styles.optionRow}>
                    <Field label={index === 0 ? 'Code' : undefined}
                           className={styles.grow}>
                        <Input id={`field-option-code-${index}`}
                               value={option.code}
                               placeholder="e.g. gold"
                               onChange={(_, d) => update(index, {code: d.value})}/>
                    </Field>
                    <Field label={index === 0 ? 'Label' : undefined}
                           className={styles.grow}>
                        <Input id={`field-option-label-${index}`}
                               value={option.label}
                               placeholder="e.g. Gold tier"
                               onChange={(_, d) => update(index, {label: d.value})}/>
                    </Field>
                    <Button id={`field-option-remove-${index}`}
                            appearance="subtle"
                            shape="circular"
                            aria-label="Remove option"
                            icon={<DeleteRegular/>}
                            onClick={() => remove(index)}/>
                </div>
            ))}
            <div>
                <Button id="field-option-add"
                        appearance="secondary"
                        shape="circular"
                        size="small"
                        icon={<AddIcon/>}
                        onClick={add}>
                    Add option
                </Button>
            </div>
        </div>
    );
};

export default OptionListEditor;
