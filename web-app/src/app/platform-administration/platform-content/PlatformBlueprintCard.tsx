import {
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Text,
} from "@fluentui/react-components";
import {MoreVerticalRegular} from "@fluentui/react-icons";
import {BlueprintDefinitionSummaryDto} from "../../models/models.tsx";
import {
    ActivateIcon,
    DeactivateIcon,
    DeleteIcon,
    EditIcon,
    PublishIcon,
    UnpublishIcon,
} from "../../components/IconBundles.tsx";
import TagList from "../../components/TagList.tsx";
import {usePlatformContentStyles} from "./PlatformContentStyles.tsx";

interface Props
{
    blueprint: BlueprintDefinitionSummaryDto;
    onEdit: () => void;
    onTogglePublished: () => void;
    onToggleActive: () => void;
    onDelete: () => void;
}

const PlatformBlueprintCard = (
    {
        blueprint,
        onEdit,
        onTogglePublished,
        onToggleActive,
        onDelete,
    }: Props) =>
{
    const styles = usePlatformContentStyles();

    return (
        <div
            id={`platform-blueprint-${blueprint.id}`}
            className={styles.blueprintCard}>
            <div
                id={`platform-blueprint-body-${blueprint.id}`}
                className={styles.blueprintCardContent}>
                <Text
                    id={`platform-blueprint-name-${blueprint.id}`}
                    weight={"semibold"}
                    size={400}>
                    {blueprint.name}
                </Text>
                {blueprint.summary && (
                    <Text
                        id={`platform-blueprint-summary-${blueprint.id}`}
                        size={200}
                        className={styles.summaryText}>
                        {blueprint.summary}
                    </Text>
                )}
                <div
                    id={`platform-blueprint-badges-${blueprint.id}`}
                    className={styles.badgeRow}>
                    <Badge
                        id={`platform-blueprint-published-${blueprint.id}`}
                        appearance={"tint"}
                        color={blueprint.isPublished ? "success" : "warning"}
                        size={"small"}>
                        {blueprint.isPublished ? "Published" : "Draft"}
                    </Badge>
                    <Badge
                        id={`platform-blueprint-active-${blueprint.id}`}
                        appearance={"tint"}
                        color={blueprint.isActive ? "success" : "warning"}
                        size={"small"}>
                        {blueprint.isActive ? "Active" : "Inactive"}
                    </Badge>
                    <TagList tags={blueprint.generalTags}/>
                </div>
            </div>
            <Menu>
                <MenuTrigger disableButtonEnhancement={true}>
                    <Button
                        id={`platform-blueprint-more-${blueprint.id}`}
                        size={"small"}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={<MoreVerticalRegular/>}
                        aria-label={`More actions for ${blueprint.name}`}/>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        <MenuItem
                            id={`platform-blueprint-edit-${blueprint.id}`}
                            icon={<EditIcon/>}
                            onClick={onEdit}>
                            Edit
                        </MenuItem>
                        <MenuItem
                            id={`platform-blueprint-publish-${blueprint.id}`}
                            icon={blueprint.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                            onClick={onTogglePublished}>
                            {blueprint.isPublished ? "Unpublish" : "Publish"}
                        </MenuItem>
                        <MenuItem
                            id={`platform-blueprint-activate-${blueprint.id}`}
                            icon={blueprint.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                            onClick={onToggleActive}>
                            {blueprint.isActive ? "Deactivate" : "Activate"}
                        </MenuItem>
                        <MenuItem
                            id={`platform-blueprint-delete-${blueprint.id}`}
                            icon={<DeleteIcon/>}
                            onClick={onDelete}>
                            Delete
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>
        </div>
    );
};

export default PlatformBlueprintCard;
