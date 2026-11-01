import React from 'react';
import {ExchangeDetailedDto} from "../../../models/models.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";

export interface ExchangeDetailsHeaderActionHandlers
{
    setIsDocumentAddDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeEndDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeRescindDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsDeletedExchangeDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeDetailedViewDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeEditDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeAccessManagementDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    exchangePermissions: ExchangePermissions;
    onRecreateRejectedExchange: (exchange: ExchangeDetailedDto) => void;
}
