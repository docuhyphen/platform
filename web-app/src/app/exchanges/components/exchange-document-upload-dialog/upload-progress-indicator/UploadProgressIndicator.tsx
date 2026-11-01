import React from "react";
import {ProgressBar, Text} from "@fluentui/react-components";

interface UploadProgressIndicatorProps
{
    progress: number;
    isServerProcessing: boolean;
}

const UploadProgressIndicator: React.FC<UploadProgressIndicatorProps> = (
    {
        progress,
        isServerProcessing
    }) =>
{
    return (
        <>
            <ProgressBar id={"exchange-document-upload-dialog-progress-bar-1"}
                         value={progress / 100}/>
            <Text id={"exchange-document-upload-dialog-text-12"}
                  size={200}>
                {isServerProcessing
                    ? "Upload complete, finalizing on server..."
                    : `Uploading... ${progress}%`}
            </Text>
        </>
    );
};

export default UploadProgressIndicator;
