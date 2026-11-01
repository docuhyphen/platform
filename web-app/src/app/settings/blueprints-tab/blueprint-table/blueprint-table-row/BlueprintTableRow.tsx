import {Badge, Text} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto} from '../../../../models/models.tsx';
import TagList from '../../../../components/TagList.tsx';
import {BlueprintListItemContext} from '../../blueprintListTypes.ts';
import BlueprintCloneMenu from '../../blueprint-clone-menu/BlueprintCloneMenu.tsx';
import BlueprintActionsMenu from '../../blueprint-actions-menu/BlueprintActionsMenu.tsx';
import {useBlueprintTableRowStyles} from './BlueprintTableRowStyles.tsx';

interface BlueprintTableRowProps
{
    blueprint: BlueprintDefinitionSummaryDto;
    context: BlueprintListItemContext;
}

const BlueprintTableRow = ({blueprint: bp, context}: BlueprintTableRowProps) =>
{
    const styles = useBlueprintTableRowStyles();
    const {activeTab, canManageItem} = context;

    return (
        <tr id={`blueprints-tab-tr-2-${bp.id}`}
            className={styles.tr}>
            <td id={"blueprints-tab-td-1"}
                className={styles.td}><Text id={"blueprints-tab-text-7"}
                                            weight="semibold">{bp.name}</Text></td>
            <td id={"blueprints-tab-td-2"}
                className={styles.td}><Text id={"blueprints-tab-text-8"}
                                            size={200}>{bp.summary ?? '-'}</Text></td>
            <td id={"blueprints-tab-td-3"}
                className={styles.td}>
                <TagList tags={bp.generalTags}/>
            </td>
            {activeTab !== 'PERSONAL' && (
                <td id={"blueprints-tab-td-4"}
                    className={styles.td}><Text id={"blueprints-tab-text-9"}
                                                size={200}>{bp.isPublished ? 'Published' : 'Draft'}</Text></td>
            )}
            <td id={"blueprints-tab-td-5"}
                className={styles.td}>
                <Badge id={"blueprints-tab-badge-3"}
                       appearance="tint"
                       color={bp.isActive ? 'success' : 'warning'}
                       size="small">
                    {bp.isActive ? 'Active' : 'Inactive'}
                </Badge>
            </td>
            <td id={"blueprints-tab-td-6"}
                className={styles.td}>
                {activeTab === 'APP' && !canManageItem(bp) && (
                    <BlueprintCloneMenu blueprint={bp}
                                        variant="table"
                                        context={context}/>
                )}
                {canManageItem(bp) && (
                    <BlueprintActionsMenu blueprint={bp}
                                          variant="table"
                                          context={context}/>
                )}
            </td>
        </tr>
    );
};

export default BlueprintTableRow;
