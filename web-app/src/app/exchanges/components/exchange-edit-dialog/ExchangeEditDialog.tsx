import {ExchangeDetailedDto, UpdateExchangeRequest} from "../../../models/models.tsx";
import React, {useEffect, useState} from "react";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    InputOnChangeData,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
    Textarea
} from "@fluentui/react-components";
import {fetchSignedInUserAppUserExchange, updateExchange} from "../../../../services/exchangeApi.ts";
import {publishExchangeUpdate} from "../../../observable/exchangeObservables.ts";
import {useExchangeEditDialogStyles} from "./ExchangeEditDialogStyles.tsx";

interface ExchangeDeleteDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    exchange: ExchangeDetailedDto;
    onExchangeEdited: (exchange: ExchangeDetailedDto) => void;
}

const ExchangeEditDialog: React.FC<ExchangeDeleteDialogProps> = (
    {
        isOpen,
        onDismiss,
        exchange,
        onExchangeEdited
    }) =>
{
    const styles = useExchangeEditDialogStyles()
    const [editingExchange, setEditingExchange] = React.useState(false);
    const [name, setExchangeName] = React.useState('')
    const [description, setDescription] = React.useState('')
    const globalStyles = useGlobalStyles()
    const [dialogErrorMessage, setDialogErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        if (exchange)
        {
            setExchangeName(exchange.name)
            setDescription(exchange.description)
            setDialogErrorMessage(null);
        }
    }, [exchange]);

    const onEdit = async () =>
    {

        setEditingExchange(true)

        try
        {
            const request = {
                name,
                description
            } as UpdateExchangeRequest

            await updateExchange(exchange.id, request);
            const updatedExchange = await fetchSignedInUserAppUserExchange(exchange.id);
            onExchangeEdited(updatedExchange as ExchangeDetailedDto);
            publishExchangeUpdate(updatedExchange as ExchangeDetailedDto)
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
    }

    const onCancel = () =>
    {
        setExchangeName('')
        setDescription('')
        onDismiss()
    }

    const onExchangeNameChange = (_e: React.ChangeEvent<HTMLInputElement>, newValue: InputOnChangeData) =>
    {
        setExchangeName(newValue.value || '');
    }

    const onDescriptionChange = (_e: React.ChangeEvent<HTMLTextAreaElement>, newValue: { value: string }) =>
    {
        setDescription(newValue.value || '');
    }

    return <>
        {<Dialog modalType="alert" open={isOpen}>
            <DialogSurface>
                <DialogBody>
                    <DialogTitle>Edit {exchange && exchange.name}</DialogTitle>
                    <DialogContent className={styles.dialogContentContainer}>
                        {dialogErrorMessage && (
                            <MessageBar intent="error">
                                <MessageBarBody>
                                    <Text size={200}>{dialogErrorMessage}</Text>
                                </MessageBarBody>
                            </MessageBar>
                        )}
                        <Field label={"Exchange name"}>
                            <Input type={"text"}
                                   value={name}
                                   onChange={onExchangeNameChange}/>
                        </Field>
                        <Field label={"Description"}>
                            <Textarea value={description}
                                      onChange={onDescriptionChange}/>
                        </Field>
                    </DialogContent>
                    <DialogActions>
                        <Button appearance="primary"
                                className={globalStyles.buttonWithLoading}
                                shape={"circular"}
                                onClick={onEdit}>
                            {editingExchange && <Spinner size={"tiny"}/>}
                            Edit
                        </Button>
                        <DialogTrigger disableButtonEnhancement>
                            <Button appearance="secondary"
                                    shape={"circular"}
                                    disabled={editingExchange}
                                    onClick={onCancel}>
                                Cancel
                            </Button>
                        </DialogTrigger>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
        }
    </>
}

export default ExchangeEditDialog;