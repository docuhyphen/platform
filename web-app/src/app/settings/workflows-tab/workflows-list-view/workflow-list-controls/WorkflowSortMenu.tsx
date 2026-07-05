import {
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Tooltip,
} from '@fluentui/react-components';
import {
    CheckmarkIcon,
    SortDownIcon,
    SortUpIcon,
} from '../../../../components/IconBundles';

export type WorkflowSortOrder = 'newest' | 'nameAsc' | 'nameDesc';

interface Props
{
    value: WorkflowSortOrder;
    onChange: (value: WorkflowSortOrder) => void;
}

const WorkflowSortMenu = ({value, onChange}: Props) => (
    <Menu>
        <MenuTrigger disableButtonEnhancement>
            <Tooltip
                content={value === 'nameAsc' ? 'Name (A-Z)' : value === 'nameDesc' ? 'Name (Z-A)' : 'Newest first'}
                relationship="description"
            >
                <Button
                    id="workflow-list-sort"
                    icon={value === 'nameDesc' ? <SortDownIcon/> : <SortUpIcon/>}
                    appearance={value === 'newest' ? 'subtle' : 'primary'}
                    shape="circular"
                />
            </Tooltip>
        </MenuTrigger>
        <MenuPopover>
            <MenuList>
                <MenuItem
                    icon={value === 'newest' ? <CheckmarkIcon/> : undefined}
                    onClick={() => onChange('newest')}
                >
                    Newest first
                </MenuItem>
                <MenuItem
                    icon={value === 'nameAsc' ? <CheckmarkIcon/> : undefined}
                    onClick={() => onChange('nameAsc')}
                >
                    Name (A-Z)
                </MenuItem>
                <MenuItem
                    icon={value === 'nameDesc' ? <CheckmarkIcon/> : undefined}
                    onClick={() => onChange('nameDesc')}
                >
                    Name (Z-A)
                </MenuItem>
            </MenuList>
        </MenuPopover>
    </Menu>
);

export default WorkflowSortMenu;
