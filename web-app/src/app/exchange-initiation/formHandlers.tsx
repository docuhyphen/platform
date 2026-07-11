import {ChangeEvent} from 'react';
import {InputOnChangeData} from '@fluentui/react-components';
import {ExchangeRequestDocumentRequest} from '../models/models.tsx';

export const handleInputChange = (setter: (value: string) => void) => (_e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>, newValue: InputOnChangeData) =>
{
    setter(newValue.value);
};

export const handleCheckboxChange = (setter: (value: boolean) => void) => (ev: ChangeEvent<HTMLInputElement>) =>
{
    setter(ev.target.checked);
};

export const handleDocumentChange = <K extends keyof ExchangeRequestDocumentRequest>(
    documents: ExchangeRequestDocumentRequest[],
    setDocuments: (docs: ExchangeRequestDocumentRequest[]) => void
) => (index: number, key: K, value: ExchangeRequestDocumentRequest[K]) =>
{
    const updatedDocuments = [...documents];
    updatedDocuments[index][key] = value;
    setDocuments(updatedDocuments);
};
