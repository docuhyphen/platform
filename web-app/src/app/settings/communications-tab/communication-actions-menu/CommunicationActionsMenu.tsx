import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {MoreVerticalRegular} from '@fluentui/react-icons';
import {
    ActivateIcon,
    CopyIcon,
    DeactivateIcon,
    DeleteIcon,
    EditIcon,
    PublishIcon,
    UnpublishIcon,
} from '../../../components/IconBundles';
import {CommunicationSummaryDto} from '../../../models/models';
import {CommunicationListActions} from '../communicationListTypes';
import {CommunicationMenuVariant, communicationActionsMenuIds} from './communicationActionsMenuIds';

interface Props
{
    communication: CommunicationSummaryDto;
    variant: CommunicationMenuVariant;
    showPublication: boolean;
    actions: CommunicationListActions;
}

const CommunicationActionsMenu = ({communication, variant, showPublication, actions}: Props) =>
{
    const ids = communicationActionsMenuIds[variant];

    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <Button
                    id={ids.trigger(communication.id)}
                    size="small"
                    appearance="subtle"
                    shape={"circular"}
                    icon={<MoreVerticalRegular/>}
                    aria-label="More actions"
                />
            </MenuTrigger>
            <MenuPopover id={ids.popover}>
                <MenuList id={ids.list}>
                    <MenuItem id={ids.edit}
                              icon={<EditIcon/>}
                              onClick={() => actions.onEdit(communication)}>Edit</MenuItem>
                    {showPublication && (
                        <MenuItem
                            id={ids.publish}
                            icon={communication.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                            onClick={() => actions.onPublish(communication)}
                        >
                            {communication.isPublished ? 'Unpublish' : 'Publish'}
                        </MenuItem>
                    )}
                    <MenuItem
                        id={ids.activate}
                        icon={communication.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                        onClick={() => actions.onActivate(communication)}
                    >
                        {communication.isActive ? 'Deactivate' : 'Activate'}
                    </MenuItem>
                    <MenuItem id={ids.duplicate}
                              icon={<CopyIcon/>}
                              onClick={() => actions.onDuplicate(communication)}>Duplicate</MenuItem>
                    <MenuItem id={ids.remove}
                              icon={<DeleteIcon/>}
                              onClick={() => actions.onDelete(communication)}>Delete</MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default CommunicationActionsMenu;
