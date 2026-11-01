import {Badge, Text} from "@fluentui/react-components";
import TagList from "../../../../components/TagList.tsx";
import {WorkflowDefinitionSummaryDto} from "../../../../models/models.tsx";
import {formatTriggerName} from "../../workflowUtils.ts";
import WorkflowActionsMenu from "../workflow-actions-menu/WorkflowActionsMenu.tsx";
import {useWorkflowCardStyles} from "./WorkflowCardStyles.tsx";

interface Props
{
    def: WorkflowDefinitionSummaryDto;
    isPersonal: boolean;
    onEdit: () => void;
    onToggleActive: () => void;
    onTogglePublished: () => void;
    onClone: () => void;
    onDelete: () => void;
}

const statusColor = (isActive: boolean): "success" | "warning" => (isActive ? "success" : "warning");

const WorkflowCard = ({def, isPersonal, onEdit, onToggleActive, onTogglePublished, onClone, onDelete}: Props) =>
{
    const styles = useWorkflowCardStyles();

    return (
        <div id={"workflows-list-view-div-1"}
             className={styles.row}>
            <div id={"workflows-list-view-div-2"}
                 className={styles.topRow}>
                <Text id={"workflows-list-view-text-1"}
                      weight="semibold">{def.name}</Text>
                <Text id={"workflows-list-view-text-2"}
                      size={200}
                      className={styles.triggerText}>{formatTriggerName(def.triggerEvent)}</Text>
            </div>

            <div id={"workflows-list-view-div-3"}
                 className={styles.middleRow}>
                <Text id={"workflows-list-view-text-3"}
                      size={200}
                      className={styles.description}>{def.summary ?? ''}</Text>
                <div id={"workflows-list-view-div-4"}
                     className={styles.middleActions}>
                    {!isPersonal && (
                        <Badge
                            id={"workflows-list-view-badge-1"}
                            color={def.isPublished ? "brand" : "subtle"}
                            appearance={def.isPublished ? "filled" : "outline"}
                            size="small"
                        >
                            {def.isPublished ? "Published" : "Draft"}
                        </Badge>
                    )}
                    <Badge id={"workflows-list-view-badge-2"}
                           color={statusColor(def.isActive)}
                           appearance="tint"
                           size="small">
                        {def.isActive ? "Active" : "Inactive"}
                    </Badge>
                    <WorkflowActionsMenu
                        variant="card"
                        def={def}
                        isPersonal={isPersonal}
                        onEdit={onEdit}
                        onToggleActive={onToggleActive}
                        onTogglePublished={onTogglePublished}
                        onClone={onClone}
                        onDelete={onDelete}
                    />
                </div>
            </div>

            <TagList tags={def.generalTags}/>
        </div>
    );
};

export default WorkflowCard;
