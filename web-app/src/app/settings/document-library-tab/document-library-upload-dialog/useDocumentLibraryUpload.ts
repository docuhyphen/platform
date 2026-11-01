import {ChangeEvent, useRef, useState} from 'react';
import {DocumentLibraryEntrySummaryDto} from '../../../models/models.tsx';
import {uploadDocumentLibraryFile} from '../../../../services/documentLibraryService.ts';
import {uploadPlatformDocumentFile} from '../../../../services/platformDocumentLibraryService.ts';
import {fileExtensionOf, isAllowedDocumentLibraryExtension, unsupportedFileTypeMessage} from './documentLibraryUploadFiles.ts';

interface DocumentLibraryUploadOptions
{
    entry: DocumentLibraryEntrySummaryDto;
    enforcedScope?: 'APP';
    onClose: () => void;
    onUploaded: () => void;
}

export const useDocumentLibraryUpload = ({entry, enforcedScope, onClose, onUploaded}: DocumentLibraryUploadOptions) =>
{
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const resetFileInput = () =>
    {
        if (fileInputRef.current) fileInputRef.current.value = '';
    };

    const handleFileChange = (e: ChangeEvent<HTMLInputElement>) =>
    {
        setSelectedFile(e.target.files?.[0] ?? null);
        setError(null);
    };

    const handleUpload = async () =>
    {
        if (!selectedFile)
        {
            setError('Please select a file');
            return;
        }
        const ext = fileExtensionOf(selectedFile);
        if (!isAllowedDocumentLibraryExtension(ext))
        {
            setError(unsupportedFileTypeMessage);
            return;
        }
        setUploading(true);
        setError(null);
        try
        {
            if (enforcedScope === 'APP')
                await uploadPlatformDocumentFile(entry, selectedFile, ext);
            else
                await uploadDocumentLibraryFile(entry.id, selectedFile, ext);
            setSelectedFile(null);
            resetFileInput();
            onUploaded();
        }
        catch
        {
            setError('Upload failed. Please try again.');
        }
        finally
        {
            setUploading(false);
        }
    };

    const handleClose = () =>
    {
        if (uploading) return;
        setSelectedFile(null);
        setError(null);
        resetFileInput();
        onClose();
    };

    const openFilePicker = () => fileInputRef.current?.click();

    return {fileInputRef, selectedFile, uploading, error, handleFileChange, handleUpload, handleClose, openFilePicker};
};
