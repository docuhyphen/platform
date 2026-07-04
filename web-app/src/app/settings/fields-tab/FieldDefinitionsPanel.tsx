import {
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Spinner,
    Text,
} from '@fluentui/react-components';
import {DeleteRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {FieldDefinitionDto, ViewMode} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';
import {VALUE_TYPE_LABELS} from './fieldLabels';

interface Props
{
    definitions: FieldDefinitionDto[];
    viewMode: ViewMode;
    canManage: boolean;
    loading: boolean;
    error: string | null;
    onRetire: (definition: FieldDefinitionDto) => void;
}

const FieldDefinitionsPanel = ({definitions, viewMode, canManage, loading, error, onRetire}: Props) =>
{
    const styles = useFieldsTabStyles();

    if (loading) return <Spinner size="small"
                                 label="Loading..."/>;
    if (error) return <Text className={styles.errorText}>{error}</Text>;
    if (definitions.length === 0) return <Text className={styles.emptyText}>No fields yet.</Text>;

    const renderActions = (definition: FieldDefinitionDto) =>
        canManage ? (
            <Menu>
                <MenuTrigger disableButtonEnhancement>
                    <Button id={`field-def-menu-${definition.id}`}
                            appearance="subtle"
                            shape="circular"
                            size="small"
                            icon={<MoreVerticalRegular/>}/>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        <MenuItem icon={<DeleteRegular/>}
                                  onClick={() => onRetire(definition)}>
                            Retire
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>
        ) : null;

    if (viewMode === 'table')
    {
        return (
            <table className={styles.table}>
                <thead>
                    <tr>
                        <th className={styles.th}>Name</th>
                        <th className={styles.th}>Key</th>
                        <th className={styles.th}>Type</th>
                        {canManage && <th className={styles.th}/>}
                    </tr>
                </thead>
                <tbody>
                    {definitions.map(definition => (
                        <tr key={definition.id}
                            className={styles.tr}>
                            <td className={styles.td}>
                                <Text weight="semibold">
                                    {definition.latestContract?.label ?? definition.fieldKey}
                                </Text>
                            </td>
                            <td className={styles.td}>
                                <code className={styles.cardFieldKey}>
                                    {definition.namespace}:{definition.fieldKey}
                                </code>
                            </td>
                            <td className={styles.td}>
                                {definition.latestContract && (
                                    <Badge appearance="tint"
                                           color="informative"
                                           size="small">
                                        {VALUE_TYPE_LABELS[definition.latestContract.valueType]}
                                    </Badge>
                                )}
                            </td>
                            {canManage && (
                                <td className={styles.td}>
                                    {renderActions(definition)}
                                </td>
                            )}
                        </tr>
                    ))}
                </tbody>
            </table>
        );
    }

    return (
        <div className={styles.cardGrid}>
            {definitions.map(definition => (
                <div key={definition.id}
                     className={styles.card}>
                    <div className={styles.cardMain}>
                        <Text className={styles.cardFieldName}
                              title={definition.latestContract?.label ?? definition.fieldKey}>
                            {definition.latestContract?.label ?? definition.fieldKey}
                        </Text>
                        <code className={styles.cardFieldKey}>
                            {definition.namespace}:{definition.fieldKey}
                        </code>
                        {definition.latestContract && (
                            <Text className={styles.cardFieldType}>
                                {VALUE_TYPE_LABELS[definition.latestContract.valueType]}
                            </Text>
                        )}
                    </div>
                    <div className={styles.cardCol2}>
                        {renderActions(definition)}
                    </div>
                </div>
            ))}
        </div>
    );
};

export default FieldDefinitionsPanel;
