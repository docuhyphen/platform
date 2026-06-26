import React, {ChangeEvent} from 'react';
import {Divider, Field, Switch} from "@fluentui/react-components";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import DownloadFormatRestriction from '../../../components/share-constraints/DownloadFormatRestriction.tsx';

interface SharingOptionsTabProps
{
    requireSignIn: boolean;
    allowDocumentAdditions: boolean;
    allowDocumentDeletions: boolean;
    allowDocumentDownload: boolean;
    allowDocumentUpdate: boolean;
    allowDocumentUpload: boolean;
    allowedDownloadFormats: string[] | undefined;
    onRequireSignInChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentAdditionsChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentDeletionsChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentDownloadChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentUpdateChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowDocumentUploadChange: (ev: ChangeEvent<HTMLInputElement>) => void;
    onAllowedDownloadFormatsChange: (formats: string[] | undefined) => void;
    locked?: boolean;
}

const SharingOptionsTab: React.FC<SharingOptionsTabProps> = (
    {
        requireSignIn,
        allowDocumentAdditions,
        allowDocumentDeletions,
        allowDocumentDownload,
        allowDocumentUpdate,
        allowDocumentUpload,
        allowedDownloadFormats,
        onRequireSignInChange,
        onAllowDocumentAdditionsChange,
        onAllowDocumentDeletionsChange,
        onAllowDocumentDownloadChange,
        onAllowDocumentUpdateChange,
        onAllowDocumentUploadChange,
        onAllowedDownloadFormatsChange,
        locked,
    }) =>
{
    const styles = useExchangeInitiationStyles();

    return (
        <div className={styles.sharingOptionsTapContent}>
            <Divider alignContent="start">Exchange options</Divider>
            <Field>
                <Switch
                    label="Require recipient sign in"
                    checked={requireSignIn}
                    onChange={onRequireSignInChange}
                    disabled={locked}
                />
            </Field>
            <Divider alignContent="start">Document options</Divider>
            <Field>
                <Switch
                    label="Allow document additions"
                    checked={allowDocumentAdditions}
                    onChange={onAllowDocumentAdditionsChange}
                    disabled={locked}
                />
            </Field>
            <Field>
                <Switch
                    label="Allow document deletions"
                    checked={allowDocumentDeletions}
                    onChange={onAllowDocumentDeletionsChange}
                    disabled={locked}
                />
            </Field>
            <Field>
                <Switch
                    label="Allow document download"
                    checked={allowDocumentDownload}
                    onChange={onAllowDocumentDownloadChange}
                    disabled={locked}
                />
            </Field>
            {allowDocumentDownload && (
                <DownloadFormatRestriction
                    allowedDownloadFormats={allowedDownloadFormats}
                    onChange={onAllowedDownloadFormatsChange}
                    disabled={locked}
                />
            )}
            <Field>
                <Switch
                    label="Allow document update"
                    checked={allowDocumentUpdate}
                    onChange={onAllowDocumentUpdateChange}
                    disabled={locked}
                />
            </Field>
            <Field>
                <Switch
                    label="Allow document upload"
                    checked={allowDocumentUpload}
                    onChange={onAllowDocumentUploadChange}
                    disabled={locked}
                />
            </Field>
        </div>
    );
};

export default SharingOptionsTab;