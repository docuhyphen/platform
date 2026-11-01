import {Text} from '@fluentui/react-components';
import {FieldDefinitionDto} from '../../../../models/models';
import FieldTypeLabel from '../../field-type-label/FieldTypeLabel';
import FieldDefinitionActionsMenu from '../field-definition-actions-menu/FieldDefinitionActionsMenu';
import {useFieldDefinitionCardStyles} from './FieldDefinitionCardStyles';

interface Props
{
    definition: FieldDefinitionDto;
    canManage: boolean;
    onRetire: (definition: FieldDefinitionDto) => void;
}

const FieldDefinitionCard = ({definition, canManage, onRetire}: Props) =>
{
    const styles = useFieldDefinitionCardStyles();

    return (
        <div id={`field-definitions-panel-div-2-${definition.id}`}
             className={styles.card}>
            <div id={"field-definitions-panel-div-3"}
                 className={styles.cardMain}>
                <Text id={"field-definitions-panel-text-4"}
                      className={styles.cardFieldName}
                      title={definition.latestContract?.label ?? definition.fieldKey}>
                    {definition.latestContract?.label ?? definition.fieldKey}
                </Text>
                <code id={"field-definitions-panel-code-2"}
                      className={styles.cardFieldKey}>
                    {definition.namespace}:{definition.fieldKey}
                </code>
                {definition.latestContract && (
                    <div id={"field-definitions-panel-div-4"}>
                        <FieldTypeLabel
                            id={`field-type-${definition.id}`}
                            valueType={definition.latestContract.valueType}
                        />
                    </div>
                )}
            </div>
            <div id={"field-definitions-panel-div-5"}
                 className={styles.cardCol2}>
                {canManage ? (
                    <FieldDefinitionActionsMenu definition={definition}
                                                onRetire={onRetire}/>
                ) : null}
            </div>
        </div>
    );
};

export default FieldDefinitionCard;
