import {Field, InfoLabel, Input, Radio, RadioGroup, Textarea} from '@fluentui/react-components';
import {SchemaTargetResourceType} from '../../../models/models';
import {useSchemaIdentityFieldsStyles} from './SchemaIdentityFieldsStyles';
import {schemaTargetOf} from '../schemaTarget';

export interface SchemaIdentity
{
    namespace: string;
    schemaKey: string;
    displayName: string;
    description: string;
    targetResourceType: SchemaTargetResourceType;
}

interface Props
{
    identity: SchemaIdentity;
    update: (patch: Partial<SchemaIdentity>) => void;
}

const SchemaIdentityFields = ({identity, update}: Props) =>
{
    const styles = useSchemaIdentityFieldsStyles();

    return (
        <>
            <div id={"schema-identity-fields-div-1"}
                 className={styles.twoColumn}>
                <Field id="schema-namespace-field"
                       label={(
                           <InfoLabel id={"schema-identity-fields-info-label-1"}
                                      info="Groups related schemas and forms the first part of the stable schema identifier. Use lowercase letters, numbers, and hyphens.">
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
                           <InfoLabel id={"schema-identity-fields-info-label-2"}
                                      info="Uniquely identifies this schema within its namespace. Use lowercase letters, numbers, and hyphens because the key remains stable after creation.">
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
            <Field id={"schema-identity-fields-field-1"}
                   label="Display name"
                   required>
                <Input id="schema-display-name"
                       value={identity.displayName}
                       placeholder="e.g. Claim Case"
                       onChange={(_, d) => update({displayName: d.value})}/>
            </Field>
            <Field id={"schema-identity-fields-field-2"}
                   label="Description">
                <Textarea id="schema-description"
                          value={identity.description}
                          rows={2}
                          onChange={(_, d) => update({description: d.value})}/>
            </Field>
            <Field id="schema-target-field"
                   label="Used for"
                   hint="Exchanges offer Exchange schemas; Information Request Templates record typed answers against Information Request schemas. This cannot change after creation.">
                <RadioGroup id="schema-target"
                            layout="horizontal"
                            value={identity.targetResourceType}
                            onChange={(_, d) => update({targetResourceType: schemaTargetOf(d.value)})}>
                    <Radio id="schema-target-exchange"
                           value={SchemaTargetResourceType.EXCHANGE}
                           label="Exchanges"/>
                    <Radio id="schema-target-information-request"
                           value={SchemaTargetResourceType.INFORMATION_REQUEST}
                           label="Information Requests"/>
                </RadioGroup>
            </Field>
        </>
    );
};

export default SchemaIdentityFields;
