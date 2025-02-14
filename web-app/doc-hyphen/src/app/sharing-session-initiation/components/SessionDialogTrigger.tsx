import React from 'react';
import { Menu, MenuButton, MenuItem, MenuList, MenuPopover, MenuTrigger } from "@fluentui/react-components";
import { Button, DialogTriggerChildProps } from "@fluentui/react-components";

const SessionDialogTrigger = React.forwardRef<HTMLButtonElement, DialogTriggerChildProps>((props, ref) => {
    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <MenuButton shape="circular" appearance="primary">
                    Start Sharing Session
                </MenuButton>
            </MenuTrigger>
            <MenuPopover>
                <MenuList>
                    <MenuItem>
                        <Button size={"small"} ref={ref} {...props} appearance={"transparent"}>
                            Request Documents
                        </Button>
                    </MenuItem>
                    <MenuItem>
                        <Button size={"small"} ref={ref} {...props} appearance={"transparent"}>
                            Send Documents
                        </Button>
                    </MenuItem>
                    <MenuItem>
                        <Button size={"small"} ref={ref} {...props} appearance={"transparent"}>
                            From template
                        </Button>
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
});

export default SessionDialogTrigger;