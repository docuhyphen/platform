import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {MoreVerticalRegular} from '@fluentui/react-icons';
import {BlueprintDefinitionSummaryDto} from '../../../models/models.tsx';
import {CopyIcon} from '../../../components/IconBundles.tsx';
import {BlueprintListItemContext, BlueprintMenuVariant} from '../blueprintListTypes.ts';
import {blueprintCloneMenuIds} from './blueprintCloneMenuIds.ts';

interface BlueprintCloneMenuProps
{
    blueprint: BlueprintDefinitionSummaryDto;
    variant: BlueprintMenuVariant;
    context: BlueprintListItemContext;
}

const BlueprintCloneMenu = ({blueprint: bp, variant, context}: BlueprintCloneMenuProps) =>
{
    const ids = blueprintCloneMenuIds[variant];
    const {canManageOrganization, actions} = context;

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
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default BlueprintCloneMenu;
