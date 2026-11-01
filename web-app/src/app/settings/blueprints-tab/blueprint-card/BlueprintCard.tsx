import {Badge, Text} from '@fluentui/react-components';
import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import TagList from '../../../components/TagList.tsx';
import {BlueprintListItemContext} from '../blueprintListTypes.ts';
import BlueprintCloneMenu from '../blueprint-clone-menu/BlueprintCloneMenu.tsx';
import BlueprintActionsMenu from '../blueprint-actions-menu/BlueprintActionsMenu.tsx';
import {useBlueprintCardStyles} from './BlueprintCardStyles.tsx';

interface BlueprintCardProps
{
    blueprint: BlueprintDefinitionSummaryDto;
    context: BlueprintListItemContext;
}

const BlueprintCard = ({blueprint: bp, context}: BlueprintCardProps) =>
{
    const styles = useBlueprintCardStyles();
    const {activeTab, canManageItem} = context;

    return (
        <div
            id={`blueprints-tab-div-10-${bp.id}`}
            className={styles.blueprintCard}
        >
            <div id={"blueprints-tab-div-11"}
                 className={styles.blueprintCardContent}>
                <Text id={"blueprints-tab-text-5"}
                      weight="semibold"
                      size={400}>{bp.name}</Text>
                {bp.summary && (
                    <Text id={"blueprints-tab-text-6"}
                          size={200}
                          className={styles.summaryText}>
                        {bp.summary}
                    </Text>
                )}
                <div id={"blueprints-tab-div-12"}
                     className={styles.badgeRow}>
                    {activeTab !== 'PERSONAL' && (
                        <Badge
                            id={"blueprints-tab-badge-1"}
                            appearance="tint"
                            color={bp.isPublished ? 'success' : 'warning'}
                            size="small"
                        >
                            {bp.isPublished ? 'Published' : 'Draft'}
                        </Badge>
                    )}
                    <Badge
                        id={"blueprints-tab-badge-2"}
                        appearance="tint"
                        color={bp.isActive ? 'success' : 'warning'}
                        size="small"
                    >
                        {bp.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                    <TagList tags={bp.generalTags}/>
                </div>
            </div>
            {activeTab === 'APP' && !canManageItem(bp) && (
                <BlueprintCloneMenu blueprint={bp}
                                    variant="card"
                                    context={context}/>
            )}
            {canManageItem(bp) && (
                <BlueprintActionsMenu blueprint={bp}
                                      variant="card"
                                      context={context}/>
            )}
        </div>
    );
};

export default BlueprintCard;
