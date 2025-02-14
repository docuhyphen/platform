import { ChangeEvent } from 'react';
import { InputOnChangeData } from '@fluentui/react-components';
import { SharingSessionRequestDocumentRequest } from '../../models/models.tsx';

export const handleInputChange = (setter: (value: string) => void) => (_e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>, newValue: InputOnChangeData) => {

    console.log("Handle Input Change", newValue.value );
    setter(newValue.value || '');
};

export const handleCheckboxChange = (setter: (value: boolean) => void) => (ev: ChangeEvent<HTMLInputElement>) => {
    setter(ev.target.checked);
};

export const handleDocumentChange = (documents: SharingSessionRequestDocumentRequest[], setDocuments: (docs: SharingSessionRequestDocumentRequest[]) => void) => (index: number, key: keyof SharingSessionRequestDocumentRequest, value: any) => {
    const updatedDocuments = [...documents];
    updatedDocuments[index][key] = value;
    setDocuments(updatedDocuments);
};