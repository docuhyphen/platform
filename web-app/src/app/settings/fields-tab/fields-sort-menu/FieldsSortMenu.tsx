import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, Tooltip} from '@fluentui/react-components';
import {CheckmarkIcon, SortDownIcon, SortUpIcon} from '../../../components/IconBundles';
import {FieldsListVariant, FieldsSortOrder} from '../fieldsListTypes';
import {FIELDS_SORT_LABELS, FIELDS_SORT_MENU_IDS} from './fieldsSortMenuIds';

interface Props
{
    variant: FieldsListVariant;
    sortOrder: FieldsSortOrder;
    onChange: (order: FieldsSortOrder) => void;
}

const FieldsSortMenu = ({variant, sortOrder, onChange}: Props) =>
{
    const ids = FIELDS_SORT_MENU_IDS[variant];

    return (
        <Menu>
            <MenuTrigger>
                <Tooltip content={FIELDS_SORT_LABELS[sortOrder]}
                         relationship="description">
                    <Button id={ids.button}
                            icon={sortOrder === 'nameDesc' ? <SortDownIcon/> : <SortUpIcon/>}
                            appearance={sortOrder !== 'default' ? 'primary' : 'subtle'}
                            shape="circular"/>
                </Tooltip>
            </MenuTrigger>
            <MenuPopover id={ids.popover}>
                <MenuList id={ids.list}>
                    <MenuItem id={ids.defaultItem}
                              icon={sortOrder === 'default' ? <CheckmarkIcon/> : undefined}
                              onClick={() => onChange('default')}>
                        {FIELDS_SORT_LABELS.default}
                    </MenuItem>
                    <MenuItem id={ids.nameAscItem}
                              icon={sortOrder === 'nameAsc' ? <CheckmarkIcon/> : undefined}
                              onClick={() => onChange('nameAsc')}>
                        {FIELDS_SORT_LABELS.nameAsc}
                    </MenuItem>
                    <MenuItem id={ids.nameDescItem}
                              icon={sortOrder === 'nameDesc' ? <CheckmarkIcon/> : undefined}
                              onClick={() => onChange('nameDesc')}>
                        {FIELDS_SORT_LABELS.nameDesc}
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default FieldsSortMenu;
