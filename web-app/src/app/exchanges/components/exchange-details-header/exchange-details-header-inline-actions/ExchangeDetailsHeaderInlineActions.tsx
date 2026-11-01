import React from 'react';
import {Button, Tooltip} from "@fluentui/react-components";
import {DocumentAddIcon, EditExchangeIcon, ManageAccessIcon} from "../../../../components/IconBundles.tsx";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import {ExchangeDetailsHeaderActionHandlers} from "../exchangeDetailsHeaderActionHandlers.ts";
import {canAddDocument, canEditSharingOptions} from "../exchangeDetailsHeaderAvailability.ts";

interface ExchangeDetailsHeaderInlineActionsProps extends ExchangeDetailsHeaderActionHandlers
{
    exchangeDetails: ExchangeDetailedDto;
}

const ExchangeDetailsHeaderInlineActions: React.FC<ExchangeDetailsHeaderInlineActionsProps> = (
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
            <Tooltip content="Add Exchange Document"
                     relationship="description">
                <Button id="exchange-details-header-add-document"
                        icon={<DocumentAddIcon/>}
                        appearance="primary"
                        shape={"circular"}
                        disabled={!canAddDocument(exchangeDetails.status, exchangePermissions)}
                        onClick={() => setIsDocumentAddDialogOpen(true)}
                />
            </Tooltip>
            <Tooltip content="Edit"
                     relationship="description">
                <Button id="exchange-details-header-edit-exchange"
                        icon={<EditExchangeIcon/>}
                        disabled={!canEditSharingOptions(exchangeDetails.status, exchangePermissions)}
                        appearance={"subtle"}
                        shape={"circular"}
                        onClick={() => setIsExchangeEditDialogOpen(true)}
                />
            </Tooltip>
            <Tooltip content="manage access"
                     relationship="description">
                <Button id="exchange-details-header-manage-access"
                        icon={<ManageAccessIcon/>}
                        disabled={!canEditSharingOptions(exchangeDetails.status, exchangePermissions)}
                        appearance={"subtle"}
                        shape={"circular"}
                        onClick={() => setIsExchangeAccessManagementDialogOpen(true)}
                />
            </Tooltip>
        </>
    );
};

export default ExchangeDetailsHeaderInlineActions;
