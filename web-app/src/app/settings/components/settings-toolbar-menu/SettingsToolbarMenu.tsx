import {ReactElement} from "react";
import {
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Tooltip
} from "@fluentui/react-components";
import {CheckmarkIcon} from "../../../components/IconBundles.tsx";

export interface SettingsToolbarMenuOption<T extends string>
{
    value: T;
    label: string;
}

interface SettingsToolbarMenuProps<T extends string>
{
    id: string;
    icon: ReactElement;
    tooltip: string;
    value: T;
    defaultValue: T;
    options: SettingsToolbarMenuOption<T>[];
    onChange: (value: T) => void;
}

const SettingsToolbarMenu = <T extends string>({
    id,
    icon,
    tooltip,
    value,
    defaultValue,
    options,
    onChange
}: SettingsToolbarMenuProps<T>) => (
    <Menu>
        <MenuTrigger>
            <Tooltip
                content={tooltip}
                relationship="description"
            >
                <Button
                    id={id}
                    icon={icon}
                    appearance={value !== defaultValue ? "primary" : "subtle"}
                    shape="circular"
                />
            </Tooltip>
        </MenuTrigger>
        <MenuPopover id={"settings-toolbar-menu-menu-popover-1"}>
            <MenuList id={"settings-toolbar-menu-menu-list-1"}>
                {options.map(option => (
                    <MenuItem
                        id={`${id}-${option.value.toLocaleLowerCase()}`}
                        key={option.value}
                        icon={value === option.value ? <CheckmarkIcon/> : undefined}
                        onClick={() => onChange(option.value)}
                    >
                        {option.label}
                    </MenuItem>
                ))}
            </MenuList>
        </MenuPopover>
    </Menu>
);

export default SettingsToolbarMenu;
