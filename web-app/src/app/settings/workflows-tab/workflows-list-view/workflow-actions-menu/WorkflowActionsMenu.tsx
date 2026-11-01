import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from "@fluentui/react-components";
import {MoreVerticalRegular} from "@fluentui/react-icons";
import {WorkflowDefinitionSummaryDto} from "../../../../models/models.tsx";
import {
    ActivateIcon,
    CopyIcon,
    DeactivateIcon,
    DeleteIcon,
    EditIcon,
    PublishIcon,
    UnpublishIcon,
} from "../../../../components/IconBundles.tsx";
import {WORKFLOW_ACTIONS_MENU_IDS, WorkflowActionsMenuVariant} from "./workflowActionsMenuIds.ts";

interface Props
{
    variant: WorkflowActionsMenuVariant;
    def: WorkflowDefinitionSummaryDto;
    isPersonal: boolean;
    onEdit: () => void;
    onToggleActive: () => void;
    onTogglePublished: () => void;
    onClone: () => void;
    onDelete: () => void;
}

const WorkflowActionsMenu = ({variant, def, isPersonal, onEdit, onToggleActive, onTogglePublished, onClone, onDelete}: Props) =>
{
    const ids = WORKFLOW_ACTIONS_MENU_IDS[variant];

    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <Button
                    id={ids.trigger(def.id)}
                    size="small"
                    appearance="subtle"
                    shape="circular"
                    icon={<MoreVerticalRegular/>}
                    aria-label="More actions"
                />
            </MenuTrigger>
            <MenuPopover id={ids.popover}>
                <MenuList id={ids.list}>
                    <MenuItem id={ids.edit}
                              icon={<EditIcon/>}
                              onClick={onEdit}>Edit</MenuItem>
                    {!isPersonal && (
                        <MenuItem
                            id={ids.publish}
                            icon={def.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                            onClick={onTogglePublished}
                        >
                            {def.isPublished ? "Unpublish" : "Publish"}
                        </MenuItem>
                    )}
                    <MenuItem
                        id={ids.active}
                        icon={def.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                        onClick={onToggleActive}
                    >
                        {def.isActive ? "Deactivate" : "Activate"}
                    </MenuItem>
                    <MenuItem id={ids.clone}
                              icon={<CopyIcon/>}
                              onClick={onClone}>Duplicate</MenuItem>
                    <MenuItem id={ids.remove}
                              icon={<DeleteIcon/>}
                              onClick={onDelete}>Delete</MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default WorkflowActionsMenu;
