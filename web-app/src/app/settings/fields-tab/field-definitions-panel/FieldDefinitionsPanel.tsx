import {Spinner, Text} from '@fluentui/react-components';
import {FieldDefinitionDto, ViewMode} from '../../../models/models';
import FieldDefinitionsTable from './field-definitions-table/FieldDefinitionsTable';
import FieldDefinitionCard from './field-definition-card/FieldDefinitionCard';
import {useFieldDefinitionsPanelStyles} from './FieldDefinitionsPanelStyles';

interface Props
{
    definitions: FieldDefinitionDto[];
    viewMode: ViewMode;
    canManage: (definition: FieldDefinitionDto) => boolean;
    loading: boolean;
    error: string | null;
    onRetire: (definition: FieldDefinitionDto) => void;
}

const FieldDefinitionsPanel = ({definitions, viewMode, canManage, loading, error, onRetire}: Props) =>
{
    const styles = useFieldDefinitionsPanelStyles();

    if (loading) return <Spinner id={"field-definitions-panel-spinner-1"}
                                 size="small"
                                 label="Loading..."/>;
    if (error) return <Text id={"field-definitions-panel-text-1"}
                            className={styles.errorText}>{error}</Text>;
    if (definitions.length === 0) return <Text id={"field-definitions-panel-text-2"}
                                               className={styles.emptyText}>No fields yet.</Text>;

    if (viewMode === 'table')
    {
        return (
            <FieldDefinitionsTable definitions={definitions}
                                   canManage={canManage}
                                   onRetire={onRetire}/>
        );
    }

    return (
        <div id={"field-definitions-panel-div-1"}
             className={styles.cardGrid}>
            {definitions.map(definition => (
                <FieldDefinitionCard key={definition.id}
                                     definition={definition}
                                     canManage={canManage(definition)}
                                     onRetire={onRetire}/>
            ))}
        </div>
    );
};

export default FieldDefinitionsPanel;
