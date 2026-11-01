import React from 'react';
import {Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, SplitButton} from "@fluentui/react-components";
import {ShareAndroidRegular} from "@fluentui/react-icons";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {InitiateFromBlueprintIcon, ReceiveDocumentsIcon, SendDocumentsIcon} from "../../../components/IconBundles.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";

interface ExchangeDialogTriggerProps
{
    onRequestingDocumentsChange: (isRequesting: boolean) => void;
    onChooseBlueprint: () => void;
    canUseBlueprints: boolean;
}

const ExchangeInitiationDialogTrigger = React.forwardRef<HTMLButtonElement, ExchangeDialogTriggerProps>((
    {
        onRequestingDocumentsChange,
        onChooseBlueprint,
        canUseBlueprints,
        ...props
    }) =>
{
    const styles = useExchangeInitiationStyles();
    const isMobile = useIsMobile();

    const {onClick: openDialog, ...triggerProps} = props as React.HTMLAttributes<HTMLElement>;

    const handleMenuItemClick =
        (action: () => void) =>
        (event: React.MouseEvent<HTMLDivElement>) =>
        {
            action();
            openDialog?.(event);
        };

    return (
        <Menu>
            <MenuTrigger disableButtonEnhancement>
                <SplitButton
                    id={"exchange-initiation-trigger"}
                    shape="circular"
                    appearance="primary"
                    icon={<ShareAndroidRegular/>}
                    menuButton={{"aria-label": "Choose sharing type"}}
                >
                    {!isMobile && "Start Exchange"}
                </SplitButton>
            </MenuTrigger>
            <MenuPopover id={"exchange-initiation-dialog-trigger-menu-popover-1"}>
                <MenuList id={"exchange-initiation-dialog-trigger-menu-list-1"}>
                    <MenuItem
                        id={"exchange-initiation-request-documents"}
                        {...triggerProps}
                        onClick={handleMenuItemClick(() => onRequestingDocumentsChange(true))}
                        className={styles.sharingDetailsInput}
                        icon={<ReceiveDocumentsIcon/>}
                    >
                        Request Documents
                    </MenuItem>
                    <MenuItem
                        id={"exchange-initiation-send-documents"}
                        {...triggerProps}
                        onClick={handleMenuItemClick(() => onRequestingDocumentsChange(false))}
                        className={styles.sharingDetailsInput}
                        icon={<SendDocumentsIcon/>}
                    >
                        Send Documents
                    </MenuItem>
                    {canUseBlueprints && (
                        <MenuItem
                            id={"exchange-initiation-from-blueprint"}
                            {...triggerProps}
                            onClick={handleMenuItemClick(() => onChooseBlueprint())}
                            icon={<InitiateFromBlueprintIcon/>}
                            className={styles.sharingDetailsInput}
                        >
                            From Blueprint
                        </MenuItem>
                    )}
                </MenuList>
            </MenuPopover>
        </Menu>
    );
});

export default ExchangeInitiationDialogTrigger;
