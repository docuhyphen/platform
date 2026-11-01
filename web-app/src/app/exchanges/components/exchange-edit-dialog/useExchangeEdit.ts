import {useEffect, useState} from "react";
import {ExchangeDetailedDto, UpdateExchangeRequest} from "../../../models/models.tsx";
import {fetchSignedInUserAppUserExchange, updateExchange} from "../../../../services/exchangeApi.ts";
import {publishExchangeUpdate} from "../../../observable/exchangeObservables.ts";

export interface ExchangeEdit
{
    name: string;
    description: string;
    editingExchange: boolean;
    dialogErrorMessage: string | null;
    setName: (name: string) => void;
    setDescription: (description: string) => void;
    onEdit: () => Promise<void>;
    onCancel: () => void;
}

export const useExchangeEdit = (
    exchange: ExchangeDetailedDto,
    onExchangeEdited: (exchange: ExchangeDetailedDto) => void,
    onDismiss: () => void,
): ExchangeEdit =>
{
    const [editingExchange, setEditingExchange] = useState(false);
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (exchange)
        {
            setName(exchange.name);
            setDescription(exchange.description);
            setDialogErrorMessage(null);
        }
    }, [exchange]);

    const onEdit = async () =>
    {
        setEditingExchange(true);

        try
        {
            const request = {name, description} as UpdateExchangeRequest;

            await updateExchange(exchange.id, request);
            const updatedExchange = await fetchSignedInUserAppUserExchange(exchange.id);
            onExchangeEdited(updatedExchange as ExchangeDetailedDto);
            publishExchangeUpdate(updatedExchange as ExchangeDetailedDto);
            onDismiss();
        }
        catch (error)
        {
            setDialogErrorMessage("Error updating exchange");
            console.error("Error updating exchange", error);
        }
        finally
        {
            setEditingExchange(false);
        }
    };

    const onCancel = () =>
    {
        setName('');
        setDescription('');
        onDismiss();
    };

    return {name, description, editingExchange, dialogErrorMessage, setName, setDescription, onEdit, onCancel};
};
