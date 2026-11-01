import {Badge, Button, Text} from "@fluentui/react-components";
import TagList from "../../../../../components/TagList.tsx";
import {WorkflowDefinitionSummaryDto} from "../../../../../models/models.tsx";
import {formatTriggerName} from "../../../workflowUtils.ts";
import WorkflowActionsMenu from "../../workflow-actions-menu/WorkflowActionsMenu.tsx";
import {WorkflowListItemActions, WorkflowListTab} from "../../workflowListTypes.ts";
import {useWorkflowTableRowStyles} from "./WorkflowTableRowStyles.tsx";

interface Props
{
    def: WorkflowDefinitionSummaryDto;
    activeTab: WorkflowListTab;
    actions: WorkflowListItemActions;
}

const WorkflowTableRow = ({def, activeTab, actions}: Props) =>
{
    const styles = useWorkflowTableRowStyles();

    return (
        <tr id={`workflows-list-view-tr-2-${def.id}`}
            className={styles.tr}>
            <td id={"workflows-list-view-td-1"}
                className={styles.td}>
                <Text id={"workflows-list-view-text-9"}
                      weight="semibold">{def.name}</Text>
                {def.summary && <Text id={"workflows-list-view-text-10"}
                                      size={200}
                                      block>{def.summary}</Text>}
            </td>
            <td id={"workflows-list-view-td-2"}
                className={styles.td}><Text id={"workflows-list-view-text-11"}
                                            size={200}>{formatTriggerName(def.triggerEvent)}</Text></td>
            <td id={"workflows-list-view-td-3"}
                className={styles.td}>
                <TagList tags={def.generalTags}/>
            </td>
            {activeTab !== 'PERSONAL' && (
                <td id={"workflows-list-view-td-4"}
                    className={styles.td}><Text id={"workflows-list-view-text-12"}
                                                size={200}>{def.isPublished ? 'Published' : 'Draft'}</Text></td>
            )}
            <td id={"workflows-list-view-td-5"}
                className={styles.td}>
                <Badge id={"workflows-list-view-badge-3"}
                       appearance="tint"
                       color={def.isActive ? 'success' : 'warning'}
                       size="small">
                    {def.isActive ? 'Active' : 'Inactive'}
                </Badge>
            </td>
            <td id={"workflows-list-view-td-6"}
                className={styles.td}>
                {activeTab === 'APP' ? (
                    <Button
                        id={`workflows-table-add-platform-btn-${def.id}`}
                        size="small"
                        appearance="outline"
                        shape="circular"
                        onClick={() => actions.onClone(def)}
                    >
                        Add to my workflows
                    </Button>
                ) : (
                    <WorkflowActionsMenu
                        variant="table"
                        def={def}
                        isPersonal={activeTab === 'PERSONAL'}
                        onEdit={() => actions.onEdit(def)}
                        onToggleActive={() => actions.onToggleActive(def)}
                        onTogglePublished={() => actions.onTogglePublished(def)}
                        onClone={() => actions.onClone(def)}
                        onDelete={() => actions.onDelete(def)}
                    />
                )}
            </td>
        </tr>
    );
};

export default WorkflowTableRow;
