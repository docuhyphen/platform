import {useEffect, useState} from 'react';
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
import {AddIcon} from '../../components/IconBundles';
import {FieldDefinitionDto, FieldLifecycleStatus} from '../../models/models';
import {listFieldDefinitions, retireFieldDefinition} from '../../../services/fieldsService';
import {useFieldsTabStyles} from './FieldsTabStyles';
import {VALUE_TYPE_LABELS} from './fieldLabels';
import FieldDefinitionDialog from './FieldDefinitionDialog';

interface Props
{
    canManage: boolean;
}

const FieldDefinitionsPanel = ({canManage}: Props) =>
{
    const styles = useFieldsTabStyles();
    const [definitions, setDefinitions] = useState<FieldDefinitionDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [dialogOpen, setDialogOpen] = useState(false);

    const load = () =>
    {
        setLoading(true);
        setError(null);
        listFieldDefinitions()
            .then(setDefinitions)
            .catch(() => setError('Failed to load fields'))
            .finally(() => setLoading(false));
    };

    useEffect(() => { load(); }, []);

    const handleRetire = async (definition: FieldDefinitionDto) =>
    {
        await retireFieldDefinition(definition.id).catch(() => null);
        load();
    };

    const activeDefinitions = definitions.filter(d => d.status !== FieldLifecycleStatus.RETIRED);

    return (
        <div className={styles.container}>
            <div className={styles.headerRow}>
                <Text size={300}
                      className={styles.descriptionText}>
                    Reusable business attributes. Compose them into schemas to attach to exchanges.
                </Text>
                {canManage && (
                    <Button id="field-def-create-btn"
                            appearance="secondary"
                            shape="circular"
                            icon={<AddIcon/>}
                            onClick={() => setDialogOpen(true)}>
                        New field
                    </Button>
                )}
            </div>

            {loading && <Spinner size="small" label="Loading..."/>}
            {!loading && error && <Text className={styles.errorText}>{error}</Text>}
            {!loading && !error && activeDefinitions.length === 0 && (
                <Text className={styles.emptyText}>No fields yet.</Text>
            )}

            <div className={styles.list}>
                {activeDefinitions.map(definition => (
                    <div key={definition.id}
                         className={styles.card}>
                        <div className={styles.cardMain}>
                            <div className={styles.cardTitleRow}>
                                <code className={styles.codeKey}>
                                    {definition.namespace}:{definition.fieldKey}
                                </code>
                                {definition.latestContract && (
                                    <Badge appearance="tint"
                                           color="informative"
                                           size="small">
                                        {VALUE_TYPE_LABELS[definition.latestContract.valueType]}
                                    </Badge>
                                )}
                            </div>
                            <Text size={200}
                                  className={styles.subText}>
                                {definition.latestContract?.label ?? '-'}
                            </Text>
                        </div>
                        {canManage && (
                            <div className={styles.actionGroup}>
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
                                                      onClick={() => handleRetire(definition)}>
                                                Retire
                                            </MenuItem>
                                        </MenuList>
                                    </MenuPopover>
                                </Menu>
                            </div>
                        )}
                    </div>
                ))}
            </div>

            <FieldDefinitionDialog open={dialogOpen}
                                   onClose={() => setDialogOpen(false)}
                                   onSaved={() => { setDialogOpen(false); load(); }}/>
        </div>
    );
};

export default FieldDefinitionsPanel;
