import {useState} from 'react';

export const useExchangeDialogsState = () =>
{
    const [isDocumentAddDialogOpen, setIsDocumentAddDialogOpen] = useState(false);
    const [isUploadDocumentDialogOpen, setIsUploadDocumentDialogOpen] = useState(false);
    const [isUpdateDocumentDialogOpen, setIsUpdateDocumentDialogOpen] = useState(false);
    const [isDocumentZipDialogOpen, setIsDocumentZipDialogOpen] = useState(false);
    const [isExchangeEditDialogOpen, setIsExchangeEditDialogOpen] = useState(false);
    const [isExchangeDetailedViewDialogOpen, setIsExchangeDetailedViewDialogOpen] = useState(false);
    const [isExchangeAccessManagementDialogOpen, setIsExchangeAccessManagementDialogOpen] = useState(false);
    const [isDeletedExchangeDialogOpen, setIsDeletedExchangeDialogOpen] = useState(false);
    const [isExchangeEndDialogOpen, setIsExchangeEndDialogOpen] = useState(false);
    const [isExchangeRescindDialogOpen, setIsExchangeRescindDialogOpen] = useState(false);

    return {
        isDocumentAddDialogOpen,
        setIsDocumentAddDialogOpen,
        isUploadDocumentDialogOpen,
        setIsUploadDocumentDialogOpen,
        isUpdateDocumentDialogOpen,
        setIsUpdateDocumentDialogOpen,
        isDocumentZipDialogOpen,
        setIsDocumentZipDialogOpen,
        isExchangeEditDialogOpen,
        setIsExchangeEditDialogOpen,
        isExchangeDetailedViewDialogOpen,
        setIsExchangeDetailedViewDialogOpen,
        isExchangeAccessManagementDialogOpen,
        setIsExchangeAccessManagementDialogOpen,
        isDeletedExchangeDialogOpen,
        setIsDeletedExchangeDialogOpen,
        isExchangeEndDialogOpen,
        setIsExchangeEndDialogOpen,
        isExchangeRescindDialogOpen,
        setIsExchangeRescindDialogOpen,
    };
};

export type ExchangeDialogsState = ReturnType<typeof useExchangeDialogsState>;
