import {useMemo, useRef, useState} from "react";
import useToken from "../../../../context/useToken.tsx";
import {uploadExchangeDocument} from "../../../../services/exchangeApi.ts";
import {DocumentDetailedDto} from "../../../models/models.tsx";
import {
    getFileExtension,
    resolveAllowedExtensions,
    resolveRestrictionLabel,
    validatePickedFile
} from "./exchangeDocumentUploadRules.ts";

export const useExchangeDocumentUpload = (
    exchangeId: string,
    exchangeDocument: DocumentDetailedDto,
    onDismiss: () => void,
    onDocumentUploaded: (document: DocumentDetailedDto) => void
) =>
{
    const token = useToken();
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState<boolean>(false);
    const [progress, setProgress] = useState<number>(0);
    const [isServerProcessing, setIsServerProcessing] = useState<boolean>(false);
    const [fileError, setFileError] = useState<string | null>(null);

    const allowedExtensions = useMemo(
        () => resolveAllowedExtensions(exchangeDocument?.restrictedType),
        [exchangeDocument?.restrictedType]
    );
    const restrictionLabel = useMemo(
        () => resolveRestrictionLabel(exchangeDocument?.restrictedType),
        [exchangeDocument?.restrictedType]
    );

    const resetState = () =>
    {
        setFile(null);
        setProgress(0);
        setFileError(null);
    };

    const onDismissDialog = () =>
    {
        resetState();
        onDismiss();
    };

    const onUploadDocument = async () =>
    {
        if (uploading || !file)
        {
            return;
        }

        setUploading(true);
        setProgress(0);
        setIsServerProcessing(false);

        const fileName = file.name;
        const formData = new FormData();
        formData.append("file", file, fileName);
        formData.append("encryptionMode", "INTERNAL");
        formData.append("extension", getFileExtension(fileName));

        try
        {
            const uploadData = await uploadExchangeDocument(exchangeId, exchangeDocument.id, formData, token, (event) =>
            {
                if (!event.total || event.total <= 0)
                {
                    setProgress((current) => Math.max(current, 10));
                    return;
                }

                const percentCompleted = Math.round((event.loaded * 100) / event.total);
                const cappedProgress = Math.min(percentCompleted, 95);
                setProgress((current) => Math.max(current, cappedProgress));

                if (percentCompleted >= 100)
                {
                    setIsServerProcessing(true);
                }
            });

            setIsServerProcessing(false);
            setProgress(100);
            onDocumentUploaded(uploadData);
            resetState();
            onDismiss();
        }
        catch (error)
        {
            setFileError("Error uploading document");
            console.error(error);
        }
        finally
        {
            setUploading(false);
            setIsServerProcessing(false);
        }
    };

    const onPickFile = (pickedFile: File | null) =>
    {
        if (!pickedFile)
        {
            setFile(null);
            setFileError(null);
            return;
        }

        const validationError = validatePickedFile(pickedFile, allowedExtensions);
        if (validationError)
        {
            setFile(null);
            setFileError(validationError);
            return;
        }

        setFileError(null);
        setFile(pickedFile);
    };

    return {
        fileInputRef,
        file,
        uploading,
        progress,
        isServerProcessing,
        fileError,
        allowedExtensions,
        restrictionLabel,
        onPickFile,
        onUploadDocument,
        onDismissDialog
    };
};
