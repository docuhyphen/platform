import React from 'react';
import {Button, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger, SplitButton} from "@fluentui/react-components";
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
    }, ref) =>
{
    const styles = useExchangeInitiationStyles();
    const isMobile = useIsMobile();

    // DialogTrigger injects an onClick (which opens the dialog) into props. Spreading
    // {...props} after each MenuItem's own onClick was overriding it, so the mode-selecting
    // handlers never ran and the dialog always opened in the default "Request Documents"
    // state. Pull that handler out and compose both: set the mode, then open the dialog.
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
                {/*
                  Mobile: icon-only (with tooltip + aria-label) to save
                  header room. Desktop/tablet: icon + "Start Exchanging"
                  label so the primary action is unambiguous at a glance.
                  Either way the SplitButton's dropdown chevron is
                  preserved so users get a visual hint that this control
                  expands into Request / Send Documents.
                */}
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
            <MenuPopover>
                <MenuList>
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
