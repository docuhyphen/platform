import {Field, InfoLabel, Input, Textarea} from '@fluentui/react-components';
import {useFieldsTabStyles} from './FieldsTabStyles';

export interface SchemaIdentity
{
    namespace: string;
    schemaKey: string;
    displayName: string;
    description: string;
}

interface Props
{
    identity: SchemaIdentity;
    update: (patch: Partial<SchemaIdentity>) => void;
}

const SchemaIdentityFields = ({identity, update}: Props) =>
{
    const styles = useFieldsTabStyles();

    return (
        <>
            <div className={styles.twoColumn}>
                <Field id="schema-namespace-field"
                       label={(
                           <InfoLabel info="Groups related schemas and forms the first part of the stable schema identifier. Use lowercase letters, numbers, and hyphens.">
                               Namespace
                           </InfoLabel>
                       )}
                       required
                       className={styles.grow}>
                    <Input id="schema-namespace"
                           value={identity.namespace}
                           placeholder="e.g. acme"
                           onChange={(_, d) => update({namespace: d.value})}/>
                </Field>
                <Field id="schema-key-field"
                       label={(
                           <InfoLabel info="Uniquely identifies this schema within its namespace. Use lowercase letters, numbers, and hyphens because the key remains stable after creation.">
                               Schema key
                           </InfoLabel>
                       )}
                       required
                       className={styles.grow}>
                    <Input id="schema-key"
                           value={identity.schemaKey}
                           placeholder="e.g. claim-case"
                           onChange={(_, d) => update({schemaKey: d.value})}/>
                </Field>
            </div>
            <Field label="Display name"
                   required>
                <Input id="schema-display-name"
                       value={identity.displayName}
                       placeholder="e.g. Claim Case"
                       onChange={(_, d) => update({displayName: d.value})}/>
            </Field>
            <Field label="Description">
                <Textarea id="schema-description"
                          value={identity.description}
                          rows={2}
                          onChange={(_, d) => update({description: d.value})}/>
            </Field>
        </>
    );
};

export default SchemaIdentityFields;
