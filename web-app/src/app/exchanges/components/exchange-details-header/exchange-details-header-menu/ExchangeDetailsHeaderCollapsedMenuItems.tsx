import React from 'react';
import {Divider, MenuItem} from "@fluentui/react-components";
import {DocumentAddIcon, EditExchangeIcon, ManageAccessIcon} from "../../../../components/IconBundles.tsx";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import {ExchangeDetailsHeaderActionHandlers} from "../exchangeDetailsHeaderActionHandlers.ts";
import {canAddDocument, canEditSharingOptions} from "../exchangeDetailsHeaderAvailability.ts";

interface ExchangeDetailsHeaderCollapsedMenuItemsProps extends ExchangeDetailsHeaderActionHandlers
{
    exchangeDetails: ExchangeDetailedDto;
}

const ExchangeDetailsHeaderCollapsedMenuItems: React.FC<ExchangeDetailsHeaderCollapsedMenuItemsProps> = (
    {
        exchangeDetails,
        exchangePermissions,
        setIsDocumentAddDialogOpen,
        setIsExchangeEditDialogOpen,
        setIsExchangeAccessManagementDialogOpen,
    }) =>
{
    return (
        <>
            <MenuItem id="exchange-details-header-menu-add-document"
                      icon={<DocumentAddIcon/>}
                      disabled={!canAddDocument(exchangeDetails.status, exchangePermissions)}
                      onClick={() => setIsDocumentAddDialogOpen(true)}>
                Add document
            </MenuItem>
            <MenuItem id="exchange-details-header-menu-edit-exchange"
                      icon={<EditExchangeIcon/>}
                      disabled={!canEditSharingOptions(exchangeDetails.status, exchangePermissions)}
                      onClick={() => setIsExchangeEditDialogOpen(true)}>
                Edit Exchange
            </MenuItem>
            <MenuItem id="exchange-details-header-menu-manage-access"
                      icon={<ManageAccessIcon/>}
                      disabled={!canEditSharingOptions(exchangeDetails.status, exchangePermissions)}
                      onClick={() => setIsExchangeAccessManagementDialogOpen(true)}>
                Manage access
            </MenuItem>
            <Divider id={"exchange-details-header-divider-1"}/>
        </>
    );
};

export default ExchangeDetailsHeaderCollapsedMenuItems;
