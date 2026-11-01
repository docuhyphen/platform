import React, {RefObject} from "react";
import {Button, Field, Text} from "@fluentui/react-components";
import {useUploadFilePickerStyles} from "./UploadFilePickerStyles.tsx";

interface UploadFilePickerProps
{
    fileInputRef: RefObject<HTMLInputElement>;
    allowedExtensions: string[];
    disabled: boolean;
    onPickFile: (file: File | null) => void;
}

const UploadFilePicker: React.FC<UploadFilePickerProps> = (
    {
        fileInputRef,
        allowedExtensions,
        disabled,
        onPickFile
    }) =>
{
    const styles = useUploadFilePickerStyles();

    return (
        <Field id={"exchange-document-upload-dialog-field-1"}
               className={styles.uploadContainer}>
            <input id={"exchange-document-upload-dialog-input-1"}
                   ref={fileInputRef}
                   className={styles.hiddenInput}
                   type="file"
                   accept={allowedExtensions.join(',')}
                   onChange={(e) => onPickFile(e.target.files ? e.target.files[0] : null)}/>
            <Button id={"upload-document-choose-file-btn"}
                    appearance="secondary"
                    shape={"circular"}
                    onClick={() => fileInputRef.current?.click()}
                    disabled={disabled}>
                Choose file
            </Button>
            <Text id={"exchange-document-upload-dialog-text-1"}
                  size={200}>Allowed: {allowedExtensions.join(', ')}</Text>
        </Field>
    );
};

export default UploadFilePicker;
