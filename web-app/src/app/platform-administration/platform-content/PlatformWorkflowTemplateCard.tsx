import {Badge, Button, Text} from "@fluentui/react-components";
import {WorkflowDefinitionSummaryDto} from "../../models/models.tsx";
import TagList from "../../components/TagList.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";

interface Props
{
    definition: WorkflowDefinitionSummaryDto;
    onEdit: () => void;
    onToggleActive: () => void;
    onTogglePublished: () => void;
    onDelete: () => void;
}

const PlatformWorkflowTemplateCard = ({
    definition,
    onEdit,
    onToggleActive,
    onTogglePublished,
    onDelete,
}: Props) =>
{
    const styles = usePlatformContentStyles();

    return (
        <article
            id={`platform-workflow-template-${definition.id}`}
            className={styles.card}>
            <div
                id={`platform-workflow-template-body-${definition.id}`}
                className={styles.cardBody}>
                <Text
                    id={`platform-workflow-template-name-${definition.id}`}
                    weight={"semibold"}>
                    {definition.name}
                </Text>
                {definition.summary && (
                    <Text id={`platform-workflow-template-summary-${definition.id}`}>
                        {definition.summary}
                    </Text>
                )}
                <div
                    id={`platform-workflow-template-badges-${definition.id}`}
                    className={styles.badges}>
                    <Badge
                        id={`platform-workflow-template-published-${definition.id}`}
                        appearance={"tint"}
                        color={definition.isPublished ? "success" : "warning"}>
                        {definition.isPublished ? "Published" : "Draft"}
                    </Badge>
                    <Badge
                        id={`platform-workflow-template-active-${definition.id}`}
                        appearance={"tint"}
                        color={definition.isActive ? "success" : "warning"}>
                        {definition.isActive ? "Active" : "Inactive"}
                    </Badge>
                </div>
                <TagList tags={definition.generalTags}/>
            </div>
            <div
                id={`platform-workflow-template-actions-${definition.id}`}
                className={styles.actions}>
                <Button
                    id={`platform-workflow-template-edit-${definition.id}`}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onEdit}>
                    Edit
                </Button>
                <Button
                    id={`platform-workflow-template-publish-${definition.id}`}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onTogglePublished}>
                    {definition.isPublished ? "Unpublish" : "Publish"}
                </Button>
                <Button
                    id={`platform-workflow-template-activate-${definition.id}`}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onToggleActive}>
                    {definition.isActive ? "Deactivate" : "Activate"}
                </Button>
                <Button
                    id={`platform-workflow-template-delete-${definition.id}`}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onDelete}>
                    Delete
                </Button>
            </div>
        </article>
    );
};

export default PlatformWorkflowTemplateCard;
