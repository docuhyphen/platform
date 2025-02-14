import React from 'react';
import { Menu, MenuButton, MenuItem, MenuList, MenuPopover, MenuTrigger } from "@fluentui/react-components";
import { Button, DialogTriggerChildProps } from "@fluentui/react-components";

interface SessionDialogTriggerProps extends DialogTriggerChildProps {
    onRequestingDocumentsChange: (isRequesting: boolean) => void;
}

const SessionDialogTrigger = React.forwardRef<HTMLButtonElement, SessionDialogTriggerProps>(({ onRequestingDocumentsChange, ...props }, ref) => {
    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <MenuButton shape="circular" appearance="primary">
                    Start Sharing Session
                </MenuButton>
            </MenuTrigger>
            <MenuPopover>
                <MenuList>
                    <MenuItem onClick={() => onRequestingDocumentsChange(true)}>
                        <Button size={"small"} ref={ref} {...props} appearance={"transparent"}>
                            Request Documents
                        </Button>
                    </MenuItem>
                    <MenuItem onClick={() => onRequestingDocumentsChange(false)}>
                        <Button size={"small"} ref={ref} {...props} appearance={"transparent"}>
                            Send Documents
                        </Button>
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
});

export default SessionDialogTrigger;