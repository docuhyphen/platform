import {Button, Switch, Text} from '@fluentui/react-components';
import {ArrowDownRegular, ArrowUpRegular, DeleteRegular} from '@fluentui/react-icons';
import {BindingDraft} from '../schemaBindingDrafts';
import SchemaBindingSettings from '../schema-binding-settings/SchemaBindingSettings';
import {useSchemaBindingRowStyles} from './SchemaBindingRowStyles';

interface Props {
    binding: BindingDraft;
    index: number;
    count: number;
    onMove: (delta: number) => void;
    onRemove: () => void;
    onChange: (patch: Partial<BindingDraft>) => void;
}

const SchemaBindingRow = ({binding, index, count, onMove, onRemove, onChange}: Props) => {
    const styles = useSchemaBindingRowStyles();

    return (
        <div id={`binding-row-${index}`}
             className={styles.row}>
            <div id={`binding-header-${index}`}
                 className={styles.header}>
                <div id={`binding-identity-${index}`}
                     className={styles.main}>
                    <Text id={`binding-label-${index}`}
                          weight="semibold">
                        {binding.label}
                    </Text>
                    <code id={`binding-key-${index}`}
                          className={styles.key}>
                        {binding.keyLabel}
                    </code>
                </div>
                <div id={`binding-actions-${index}`}
                     className={styles.actions}>
                    <Button id={`binding-up-${index}`}
                            appearance="subtle"
                            shape="circular"
                            size="small"
                            aria-label="Move up"
                            disabled={index === 0}
                            icon={<ArrowUpRegular/>}
                            onClick={() => onMove(-1)}/>
                    <Button id={`binding-down-${index}`}
                            appearance="subtle"
                            shape="circular"
                            size="small"
                            aria-label="Move down"
                            disabled={index === count - 1}
                            icon={<ArrowDownRegular/>}
                            onClick={() => onMove(1)}/>
                    <Button id={`binding-remove-${index}`}
                            appearance="subtle"
                            shape="circular"
                            size="small"
                            aria-label="Remove field"
                            icon={<DeleteRegular/>}
                            onClick={onRemove}/>
                </div>
            </div>
            <div id={`binding-toggles-${index}`}
                 className={styles.toggles}>
                <Switch id={`binding-required-${index}`}
                        checked={binding.isRequired}
                        label="Required"
                        onChange={(_, d) => onChange({isRequired: d.checked})}/>
                <Switch id={`binding-readonly-${index}`}
                        checked={binding.isReadOnly}
                        label="Read only"
                        onChange={(_, d) => onChange({isReadOnly: d.checked})}/>
            </div>
            <SchemaBindingSettings binding={binding}
                                   index={index}
                                   onChange={onChange}/>
        </div>
    );
};

export default SchemaBindingRow;
