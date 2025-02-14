import React, {ChangeEvent} from 'react';
import {Divider, Field, Switch,} from "@fluentui/react-components";

interface SharingOptionsTabProps
{
    requireSignIn: boolean;
    allowDocumentAdditions: boolean;
    allowDocumentDeletions: boolean;
    allowDocumentDownload: boolean;
    allowDocumentUpdate: boolean;
    allowDocumentUpload: boolean;
    onRequireSignInChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentAdditionsChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentDeletionsChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentDownloadChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentUpdateChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentUploadChange: (ev: ChangeEvent<HTMLInputElement>) => void;
}

const SharingOptionsTab: React.FC<SharingOptionsTabProps> = ({
                                                                 requireSignIn,
                                                                 allowDocumentAdditions,
                                                                 allowDocumentDeletions,
                                                                 allowDocumentDownload,
                                                                 allowDocumentUpdate,
                                                                 allowDocumentUpload,
                                                                 onRequireSignInChange,
                                                                 onAllowDocumentAdditionsChange,
                                                                 onAllowDocumentDeletionsChange,
                                                                 onAllowDocumentDownloadChange,
                                                                 onAllowDocumentUpdateChange,
                                                                 onAllowDocumentUploadChange
                                                             }) =>
{
    return (
        <div id="sharing-options-tap-content">
            <Divider alignContent="start">Session options</Divider>
            <Field>
                <Switch label="Require recipient sign in"
                        checked={requireSignIn}
                        onChange={onRequireSignInChange}/>
            </Field>
            <Divider alignContent="start">Document options</Divider>
            <Field>
                <Switch label="Allow document additions"
                        checked={allowDocumentAdditions}
                        onChange={onAllowDocumentAdditionsChange}/>
            </Field>
            <Field>
                <Switch label="Allow document deletions"
                        checked={allowDocumentDeletions}
                        onChange={onAllowDocumentDeletionsChange}/>
            </Field>
            <Field>
                <Switch label="Allow document Download"
                        checked={allowDocumentDownload}
                        onChange={onAllowDocumentDownloadChange}/>
            </Field>
            <Field>
                <Switch label="Allow document update"
                        checked={allowDocumentUpdate}
                        onChange={onAllowDocumentUpdateChange}/>
            </Field>
            <Field>
                <Switch label="Allow document upload"
                        checked={allowDocumentUpload}
                        onChange={onAllowDocumentUploadChange}/>
            </Field>
        </div>
    );
};

export default SharingOptionsTab;