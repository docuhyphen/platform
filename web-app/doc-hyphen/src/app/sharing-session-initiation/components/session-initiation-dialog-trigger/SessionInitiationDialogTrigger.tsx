import React from 'react';
import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, SplitButton} from "@fluentui/react-components";
import {useSharingSessionInitiationStyles} from "../../SharingSessionInitiationStyles.tsx";
import {ReceiveDocumentsIcon, SendDocumentsIcon} from "../../../components/IconBundles.tsx";

interface SessionDialogTriggerProps
{
    onRequestingDocumentsChange: (isRequesting: boolean) => void;
}

const SessionInitiationDialogTrigger = React.forwardRef<HTMLButtonElement, SessionDialogTriggerProps>((
    {
        onRequestingDocumentsChange,
        ...props
    }, ref) =>
{
    const styles = useSharingSessionInitiationStyles();

    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <SplitButton shape="circular" appearance="primary">
                    Start Sharing
                </SplitButton>
            </MenuTrigger>
            <MenuPopover>
                <MenuList>
                    <MenuItem onClick={() => onRequestingDocumentsChange(true)}>
                        <Button size="small" ref={ref} {...props} appearance="transparent"
                                className={styles.sharingDetailsInput}
                                icon={<ReceiveDocumentsIcon/>}>
                            Request Documents
                        </Button>
                    </MenuItem>
                    <MenuItem onClick={() => onRequestingDocumentsChange(false)}>
                        <Button size="small" ref={ref} {...props} appearance="transparent"
                                className={styles.sharingDetailsInput}
                                icon={<SendDocumentsIcon/>}>
                            Send Documents
                        </Button>
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
});

export default SessionInitiationDialogTrigger;