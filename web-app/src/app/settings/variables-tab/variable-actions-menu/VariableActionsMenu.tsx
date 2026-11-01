import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {DeleteRegular, EditRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {VariableDefinitionDto} from '../../../models/models';
import {VariableMenuIds} from '../variableIds.ts';

interface VariableActionsMenuProps
{
    variable: VariableDefinitionDto;
    ids: VariableMenuIds;
    onEdit: (v: VariableDefinitionDto) => void;
    onDelete: (v: VariableDefinitionDto) => void;
}

const VariableActionsMenu = ({variable, ids, onEdit, onDelete}: VariableActionsMenuProps) => (
    <Menu>
        <MenuTrigger disableButtonEnhancement>
            <Button
                id={ids.trigger}
                size="small"
                appearance="subtle"
                shape={"circular"}
                icon={<MoreVerticalRegular/>}
            />
        </MenuTrigger>
        <MenuPopover id={ids.popover}>
            <MenuList id={ids.list}>
                <MenuItem
                    id={ids.edit}
                    icon={<EditRegular/>}
                    onClick={() => onEdit(variable)}
                >
                    Edit
                </MenuItem>
                <MenuItem
                    id={ids.remove}
                    icon={<DeleteRegular/>}
                    onClick={() => onDelete(variable)}
                >
                    Delete
                </MenuItem>
            </MenuList>
        </MenuPopover>
    </Menu>
);

export default VariableActionsMenu;
