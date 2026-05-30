import React from 'react';
import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, SplitButton, Tooltip} from "@fluentui/react-components";
import {ShareAndroidRegular} from "@fluentui/react-icons";
import {useSharingSessionInitiationStyles} from "../../SharingSessionInitiationStyles.tsx";
import {ReceiveDocumentsIcon, SendDocumentsIcon} from "../../../components/IconBundles.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";

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
    const isMobile = useIsMobile();

    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                {/*
                  Mobile: icon-only (with tooltip + aria-label) to save
                  header room. Desktop/tablet: icon + "Start Sharing"
                  label so the primary action is unambiguous at a glance.
                  Either way the SplitButton's dropdown chevron is
                  preserved so users get a visual hint that this control
                  expands into Request / Send Documents.
                */}
                <Tooltip content="Start sharing" relationship="label">
                    <SplitButton
                        shape="circular"
                        appearance="primary"
                        icon={<ShareAndroidRegular/>}
                        aria-label="Start sharing"
                        menuButton={{"aria-label": "Choose sharing type"}}
                    >
                        {!isMobile && "Start Sharing"}
                    </SplitButton>
                </Tooltip>
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