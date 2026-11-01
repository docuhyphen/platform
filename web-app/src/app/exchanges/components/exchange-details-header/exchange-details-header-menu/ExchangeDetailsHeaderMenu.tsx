import React from 'react';
import {Button, Divider, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger} from "@fluentui/react-components";
import {MoreVerticalRegular} from "@fluentui/react-icons";
import {
    DeleteIcon,
    EditExchangeIcon,
    ExchangeDetailedViewIcon,
    ExchangeEndIcon
} from "../../../../components/IconBundles.tsx";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import {ExchangeDetailsHeaderActionHandlers} from "../exchangeDetailsHeaderActionHandlers.ts";
import {canEndExchange, canRecreateRejectedExchange, canRescindExchange} from "../exchangeDetailsHeaderAvailability.ts";
import ExchangeDetailsHeaderCollapsedMenuItems from "./ExchangeDetailsHeaderCollapsedMenuItems.tsx";

interface ExchangeDetailsHeaderMenuProps extends ExchangeDetailsHeaderActionHandlers
{
    exchangeDetails: ExchangeDetailedDto;
    showCollapsedActions: boolean;
}

const ExchangeDetailsHeaderMenu: React.FC<ExchangeDetailsHeaderMenuProps> = (props) =>
{
    const {
        exchangeDetails,
        showCollapsedActions,
        exchangePermissions,
        setIsExchangeDetailedViewDialogOpen,
        setIsExchangeRescindDialogOpen,
        setIsExchangeEndDialogOpen,
        setIsDeletedExchangeDialogOpen,
        onRecreateRejectedExchange,
    } = props;

    return (
        <Menu positioning={{autoSize: true}}>
            <MenuTrigger disableButtonEnhancement>
                <Button id="exchange-details-header-more-menu-trigger"
                        icon={<MoreVerticalRegular/>}
                        appearance="subtle"
                        shape={"circular"}
                />
            </MenuTrigger>
            <MenuPopover id={"exchange-details-header-menu-popover-1"}>
                <MenuList id="exchange-details-header-more-menu-list">
                    {showCollapsedActions && (
                        <ExchangeDetailsHeaderCollapsedMenuItems {...props}/>
                    )}
                    <MenuItem id="exchange-details-header-menu-more-info"
                              icon={<ExchangeDetailedViewIcon/>}
                              onClick={() => setIsExchangeDetailedViewDialogOpen(true)}>
                        More info
                    </MenuItem>
                    {canRecreateRejectedExchange(exchangeDetails.status, exchangePermissions) && (
                        <MenuItem id="exchange-details-header-menu-recreate-request"
                                  icon={<EditExchangeIcon/>}
                                  onClick={() => onRecreateRejectedExchange(exchangeDetails)}>
                            Recreate Request
                        </MenuItem>
                    )}
                    <Divider id={"exchange-details-header-divider-2"}/>
                    <MenuItem id="exchange-details-header-menu-rescind"
                              icon={<DeleteIcon/>}
                              disabled={!canRescindExchange(exchangeDetails.status, exchangePermissions)}
                              onClick={() => setIsExchangeRescindDialogOpen(true)}>
                        Rescind
                    </MenuItem>
                    <MenuItem id="exchange-details-header-menu-end"
                              icon={<ExchangeEndIcon/>}
                              disabled={!canEndExchange(exchangeDetails.status, exchangePermissions)}
                              onClick={() => setIsExchangeEndDialogOpen(true)}>
                        End
                    </MenuItem>
                    <MenuItem id="exchange-details-header-menu-delete"
                              icon={<DeleteIcon/>}
                              disabled={!exchangePermissions.canDeleteExchange}
                              onClick={() => setIsDeletedExchangeDialogOpen(true)}>
                        Delete
                    </MenuItem>
                </MenuList>
            </MenuPopover>
        </Menu>
    );
};

export default ExchangeDetailsHeaderMenu;
