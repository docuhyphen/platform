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
}

const ExchangeInitiationDialogTrigger = React.forwardRef<HTMLButtonElement, ExchangeDialogTriggerProps>((
    {
        onRequestingDocumentsChange,
        onChooseBlueprint,
        ...props
    }, ref) =>
{
    const styles = useExchangeInitiationStyles();
    const isMobile = useIsMobile();

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
                    <MenuItem onClick={() => onRequestingDocumentsChange(true)}
                              {...props}
                              appearance="transparent"
                              className={styles.sharingDetailsInput}
                              icon={<ReceiveDocumentsIcon/>}>
                            Request Documents
                    </MenuItem>
                    <MenuItem onClick={() => onRequestingDocumentsChange(false)}
                              {...props}
                              className={styles.sharingDetailsInput}
                              icon={<SendDocumentsIcon/>}>
                        Send Documents
                    </MenuItem>
                    <MenuItem onClick={() => onChooseBlueprint()}
                              icon={<InitiateFromBlueprintIcon/>}
                              {...props}
                              className={styles.sharingDetailsInput}>
                        From Blueprint
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
});

export default ExchangeInitiationDialogTrigger;