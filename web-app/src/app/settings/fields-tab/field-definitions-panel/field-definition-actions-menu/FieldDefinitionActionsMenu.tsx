import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {DeleteRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {FieldDefinitionDto} from '../../../../models/models';

interface Props
{
    definition: FieldDefinitionDto;
    onRetire: (definition: FieldDefinitionDto) => void;
}

const FieldDefinitionActionsMenu = ({definition, onRetire}: Props) =>
    (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <Button id={`field-def-menu-${definition.id}`}
                        appearance="subtle"
                        shape="circular"
                        size="small"
                        icon={<MoreVerticalRegular/>}/>
            </MenuTrigger>
            <MenuPopover id={"field-definitions-panel-menu-popover-1"}>
                <MenuList id={"field-definitions-panel-menu-list-1"}>
                    <MenuItem id={"field-definitions-panel-menu-item-1"}
                              icon={<DeleteRegular/>}
                              onClick={() => onRetire(definition)}>
                        Retire
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );

export default FieldDefinitionActionsMenu;
