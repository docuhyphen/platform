import {Badge, Button, Text} from "@fluentui/react-components";
import {CommunicationSummaryDto} from "../../models/models.tsx";
import TagList from "../../components/TagList.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";

interface Props
{
    communication: CommunicationSummaryDto;
    onEdit: () => void;
    onTogglePublished: () => void;
    onToggleActive: () => void;
    onDelete: () => void;
}

const PlatformCommunicationCard = ({
    communication,
    onEdit,
    onTogglePublished,
    onToggleActive,
    onDelete,
}: Props) =>
{
    const styles = usePlatformContentStyles();

    return (
        <article
            id={`platform-communication-${communication.id}`}
            className={styles.card}>
            <div
                id={`platform-communication-body-${communication.id}`}
                className={styles.cardBody}>
                <Text
                    id={`platform-communication-name-${communication.id}`}
                    weight={"semibold"}>
                    {communication.name}
                </Text>
                <Text id={`platform-communication-subject-${communication.id}`}>
                    {communication.subject}
                </Text>
                <div
                    id={`platform-communication-badges-${communication.id}`}
                    className={styles.badges}>
                    <Badge
                        id={`platform-communication-published-${communication.id}`}
                        appearance={"tint"}
                        color={communication.isPublished ? "success" : "warning"}>
                        {communication.isPublished ? "Published" : "Draft"}
                    </Badge>
                    <Badge
                        id={`platform-communication-active-${communication.id}`}
                        appearance={"tint"}
                        color={communication.isActive ? "success" : "warning"}>
                        {communication.isActive ? "Active" : "Inactive"}
                    </Badge>
                </div>
                <TagList tags={communication.generalTags}/>
            </div>
            <div
                id={`platform-communication-actions-${communication.id}`}
                className={styles.actions}>
                <Button
                    id={`platform-communication-edit-${communication.id}`}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onEdit}>
                    Edit
                </Button>
                <Button
                    id={`platform-communication-publish-${communication.id}`}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onTogglePublished}>
                    {communication.isPublished ? "Unpublish" : "Publish"}
                </Button>
                <Button
                    id={`platform-communication-activate-${communication.id}`}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onToggleActive}>
                    {communication.isActive ? "Deactivate" : "Activate"}
                </Button>
                <Button
                    id={`platform-communication-delete-${communication.id}`}
                    appearance={"secondary"}
                    shape={"circular"}
                    onClick={onDelete}>
                    Delete
                </Button>
            </div>
        </article>
    );
};

export default PlatformCommunicationCard;
