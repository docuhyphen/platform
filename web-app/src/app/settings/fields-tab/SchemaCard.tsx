import {
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Text,
} from '@fluentui/react-components';
import {AddRegular, BranchRegular, DeleteRegular, EditRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {SchemaDefinitionDto, ViewMode} from '../../models/models';
import {useFieldsTabStyles} from './FieldsTabStyles';

interface Props
{
    schema: SchemaDefinitionDto;
    canManage: boolean;
    viewMode: ViewMode;
    onEdit: (schema: SchemaDefinitionDto) => void;
    onPublish: (schema: SchemaDefinitionDto) => void;
    onNewVersion: (schema: SchemaDefinitionDto) => void;
    onRetire: (schema: SchemaDefinitionDto) => void;
}

const SchemaCard = ({schema, canManage, viewMode, onEdit, onPublish, onNewVersion, onRetire}: Props) =>
{
    const styles = useFieldsTabStyles();

    const actionsMenu = canManage ? (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <Button id={`schema-menu-${schema.id}`}
                        appearance="subtle"
                        shape="circular"
                        size="small"
                        icon={<MoreVerticalRegular/>}/>
            </MenuTrigger>
            <MenuPopover>
                <MenuList>
                    {schema.draftVersion && (
                        <MenuItem icon={<EditRegular/>}
                                  onClick={() => onEdit(schema)}>
                            Edit fields
                        </MenuItem>
                    )}
                    {schema.draftVersion && (
                        <MenuItem icon={<BranchRegular/>}
                                  onClick={() => onPublish(schema)}>
                            Publish draft
                        </MenuItem>
                    )}
                    {!schema.draftVersion && (
                        <MenuItem icon={<AddRegular/>}
                                  onClick={() => onNewVersion(schema)}>
                            New version
                        </MenuItem>
                    )}
                    <MenuItem icon={<DeleteRegular/>}
                              onClick={() => onRetire(schema)}>
                        Retire
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    ) : null;

    const statusBadges = (
        <div className={styles.badgeRow}>
            {schema.latestPublishedVersion && (
                <Badge appearance="tint"
                       color="success"
                       size="small">
                    Published v{schema.latestPublishedVersion.versionNumber}
                </Badge>
            )}
            {schema.draftVersion && (
                <Badge appearance="tint"
                       color="warning"
                       size="small">
                    Draft v{schema.draftVersion.versionNumber}
                </Badge>
            )}
        </div>
    );

    if (viewMode === 'table')
    {
        return (
            <tr className={styles.tr}>
                <td className={styles.td}>
                    <Text weight="semibold">{schema.displayName}</Text>
                </td>
                <td className={styles.td}>
                    <code className={styles.cardFieldKey}>
                        {schema.namespace}:{schema.schemaKey}
                    </code>
                </td>
                <td className={styles.td}>
                    {statusBadges}
                </td>
                {canManage && (
                    <td className={styles.td}>
                        {actionsMenu}
                    </td>
                )}
            </tr>
        );
    }

    return (
        <div className={styles.card}>
            <div className={styles.cardMain}>
                <Text className={styles.cardFieldName}
                      title={schema.displayName}>
                    {schema.displayName}
                </Text>
                {schema.description && (
                    <Text className={styles.subText}
                          title={schema.description}>
                        {schema.description}
                    </Text>
                )}
                <code className={styles.codeKey}>
                    {schema.namespace}:{schema.schemaKey}
                </code>
            </div>

            <div className={styles.schemaCardCol2}>
                {statusBadges}
                {actionsMenu}
            </div>
        </div>
    );
};

export default SchemaCard;
