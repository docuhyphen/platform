import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from '@fluentui/react-components';
import {ArrowCounterclockwiseRegular, DeleteRegular, EditRegular, MoreVerticalRegular} from '@fluentui/react-icons';
import {SequenceDefinitionDto} from '../../../models/models';
import {SequenceMenuIds} from '../sequenceMenuIds.ts';

interface SequenceActionsMenuProps
{
    sequence: SequenceDefinitionDto;
    ids: SequenceMenuIds;
    onEdit: (seq: SequenceDefinitionDto) => void;
    onReset: (seq: SequenceDefinitionDto) => void;
    onDelete: (seq: SequenceDefinitionDto) => void;
}

const SequenceActionsMenu = ({sequence, ids, onEdit, onReset, onDelete}: SequenceActionsMenuProps) => (
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
                    onClick={() => onEdit(sequence)}
                >
                    Edit
                </MenuItem>
                <MenuItem
                    id={ids.reset}
                    icon={<ArrowCounterclockwiseRegular/>}
                    onClick={() => onReset(sequence)}
                >
                    Reset Counter
                </MenuItem>
                <MenuItem
                    id={ids.remove}
                    icon={<DeleteRegular/>}
                    onClick={() => onDelete(sequence)}
                >
                    Delete
                </MenuItem>
            </MenuList>
        </MenuPopover>
    </Menu>
);

export default SequenceActionsMenu;
