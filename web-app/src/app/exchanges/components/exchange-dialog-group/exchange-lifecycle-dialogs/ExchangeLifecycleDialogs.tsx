import React from 'react';
import ExchangeDeleteDialog from '../../exchange-delete-dialog/ExchangeDeleteDialog.tsx';
import ExchangeEndDialog from '../../exchange-end-dialog/ExchangeEndDialog.tsx';
import {ExchangeDetailedDto} from '../../../../models/models.tsx';
import ExchangeEditDialog from "../../exchange-edit-dialog/ExchangeEditDialog.tsx";
import ExchangeAccessManagementDialog
    from "../../exchange-access-management-dialog/ExchangeAccessManagementDialog.tsx";
import ExchangeDetailedViewDialog from "../../exchange-detailed-view-dialog/ExchangeDetailedViewDialog.tsx";
import ExchangeRescindDialog from "../../exchange-rescind-dialog/ExchangeRescindDialog.tsx";
import {ExchangeDialogsState} from '../useExchangeDialogsState.ts';

export interface ExchangeLifecycleDialogHandlers
{
    onExchangeDeleted: (exchangeId: string) => void;
    onExchangeEnded: (exchange: ExchangeDetailedDto) => void;
    onExchangeRescinded: (exchange: ExchangeDetailedDto) => void;
    onExchangeEdited: (exchange: ExchangeDetailedDto) => void;
    onExchangeAccessManagementUpdated: (exchange: ExchangeDetailedDto) => void;
}

interface ExchangeLifecycleDialogsProps extends ExchangeLifecycleDialogHandlers
{
    dialogs: ExchangeDialogsState;
    exchangeDetails: ExchangeDetailedDto | null;
}

const ExchangeLifecycleDialogs: React.FC<ExchangeLifecycleDialogsProps> = (
    {
        dialogs,
        exchangeDetails,
        onExchangeDeleted,
        onExchangeEnded,
        onExchangeRescinded,
        onExchangeEdited,
        onExchangeAccessManagementUpdated,
    }) =>
{
    return (
        <>
            <ExchangeDeleteDialog isOpen={dialogs.isDeletedExchangeDialogOpen}
                                  onDismiss={() => dialogs.setIsDeletedExchangeDialogOpen(false)}
                                  exchange={exchangeDetails}
                                  onExchangeDeleted={onExchangeDeleted}/>

            <ExchangeEditDialog isOpen={dialogs.isExchangeEditDialogOpen}
                                onDismiss={() => dialogs.setIsExchangeEditDialogOpen(false)}
                                exchange={exchangeDetails}
                                onExchangeEdited={onExchangeEdited}/>

            <ExchangeEndDialog isOpen={dialogs.isExchangeEndDialogOpen}
                               onDismiss={() => dialogs.setIsExchangeEndDialogOpen(false)}
                               exchange={exchangeDetails}
                               onExchangeEnded={onExchangeEnded}/>

            <ExchangeRescindDialog
                isOpen={dialogs.isExchangeRescindDialogOpen}
                onDismiss={() => dialogs.setIsExchangeRescindDialogOpen(false)}
                exchange={exchangeDetails}
                onExchangeRescinded={onExchangeRescinded}
            />

            <ExchangeAccessManagementDialog isOpen={dialogs.isExchangeAccessManagementDialogOpen}
                                            onDismiss={() => dialogs.setIsExchangeAccessManagementDialogOpen(false)}
                                            exchange={exchangeDetails}
                                            onExchangeAccessManagementUpdated={onExchangeAccessManagementUpdated}/>

            <ExchangeDetailedViewDialog isOpen={dialogs.isExchangeDetailedViewDialogOpen}
                                        onDismiss={() => dialogs.setIsExchangeDetailedViewDialogOpen(false)}
                                        exchange={exchangeDetails}/>
        </>
    );
};

export default ExchangeLifecycleDialogs;
