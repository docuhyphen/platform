import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {MoreVerticalRegular} from '@fluentui/react-icons';
import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {
    ActivateIcon,
    CopyIcon,
    DeactivateIcon,
    DeleteIcon,
    EditIcon,
    PublishIcon,
    UnpublishIcon,
} from '../../../components/IconBundles.tsx';
import {BlueprintListItemContext, BlueprintMenuVariant} from '../blueprintListTypes.ts';
import {blueprintActionsMenuIds} from './blueprintActionsMenuIds.ts';

interface BlueprintActionsMenuProps
{
    blueprint: BlueprintDefinitionSummaryDto;
    variant: BlueprintMenuVariant;
    context: BlueprintListItemContext;
}

const BlueprintActionsMenu = ({blueprint: bp, variant, context}: BlueprintActionsMenuProps) =>
{
    const ids = blueprintActionsMenuIds[variant];
    const {activeTab, canManageOrganization, actions} = context;

    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <Button
                    id={`${ids.buttonPrefix}${bp.id}`}
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
                              onClick={() => actions.onEdit(bp)}>Edit</MenuItem>
                    {activeTab !== 'PERSONAL' && (
                        <MenuItem
                            id={ids.publish}
                            icon={bp.isPublished ? <UnpublishIcon/> : <PublishIcon/>}
                            onClick={() => actions.onPublish(bp)}
                        >
                            {bp.isPublished ? 'Unpublish' : 'Publish'}
                        </MenuItem>
                    )}
                    <MenuItem
                        id={ids.activate}
                        icon={bp.isActive ? <DeactivateIcon/> : <ActivateIcon/>}
                        onClick={() => actions.onActivate(bp)}
                    >
                        {bp.isActive ? 'Deactivate' : 'Activate'}
                    </MenuItem>
                    {activeTab === 'APP' ? (
                        <>
                            <MenuItem id={ids.cloneToPersonal}
                                      icon={<CopyIcon/>}
                                      onClick={() => actions.onCloneToPersonal(bp)}>
                                Clone to My Collection
                            </MenuItem>
                            {canManageOrganization && (
                                <MenuItem id={ids.cloneToOrg}
                                          icon={<CopyIcon/>}
                                          onClick={() => actions.onCloneToOrg(bp)}>
                                    Clone to Organization
                                </MenuItem>
                            )}
                        </>
                    ) : (
                        <MenuItem id={ids.duplicate}
                                  icon={<CopyIcon/>}
                                  onClick={() => actions.onDuplicate(bp)}>Duplicate</MenuItem>
                    )}
                    <MenuItem id={ids.remove}
                              icon={<DeleteIcon/>}
                              onClick={() => actions.onDelete(bp)}>Delete</MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default BlueprintActionsMenu;
