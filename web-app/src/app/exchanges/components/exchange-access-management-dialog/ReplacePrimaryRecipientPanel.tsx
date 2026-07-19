import React, {useCallback, useState} from 'react';
import {Button, MessageBar, MessageBarBody, Spinner, Text} from '@fluentui/react-components';
import {useReplacePrimaryRecipientPanelStyles} from './ReplacePrimaryRecipientPanelStyles.tsx';
import {BackIcon} from '../../../components/IconBundles.tsx';
import {OrganizationBasicDto} from '../../../models/models.tsx';
import {OrganizationGroupBasicDto} from '../../../../services/organizationApi.ts';
import {ExternalIdentityResolution} from '../../../../services/organizationTrust.ts';
import TrustedOrganizationRecipients
    from '../../../exchange-initiation/components/exchange-initiation-recipients-tab/trusted-organization-recipients/TrustedOrganizationRecipients.tsx';
import {buildRecipientSelection} from '../../../exchange-initiation/exchangeInitiationRecipientSelection.ts';
import {ExchangeInitiationRecipientMode}
    from '../../../exchange-initiation/components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts';
import {replaceExchangePrimaryRecipient} from '../../../../services/exchangeApi.ts';
import {normalizeApiError} from '../../../../utils/apiErrorUtils.ts';

interface ReplacePrimaryRecipientPanelProps
{
    exchangeId: string;
    onBack: () => void;
    onReplaced: () => void;
}

const ReplacePrimaryRecipientPanel: React.FC<ReplacePrimaryRecipientPanelProps> = ({exchangeId, onBack, onReplaced}) =>
{
    const styles = useReplacePrimaryRecipientPanelStyles();
    const [recipientOrg, setRecipientOrg] = useState<OrganizationBasicDto | undefined>(undefined);
    const [recipientOrgGroup, setRecipientOrgGroup] = useState<OrganizationGroupBasicDto | undefined>(undefined);
    const [resolution, setResolution] = useState<ExternalIdentityResolution | undefined>(undefined);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const clearRecipientUser = useCallback(() => undefined, []);

    const canSubmit = Boolean(resolution?.id) || Boolean(recipientOrgGroup?.id);

    const onConfirm = async () =>
    {
        if (!canSubmit || submitting)
        {
            return;
        }
        setSubmitting(true);
        setError(null);
        try
        {
            const selection = buildRecipientSelection({
                mode: ExchangeInitiationRecipientMode.TRUSTED_ORG,
                organization: recipientOrg,
                group: recipientOrgGroup,
                resolutionId: resolution?.id,
            });
            await replaceExchangePrimaryRecipient(exchangeId, selection);
            onReplaced();
        }
        catch (err: unknown)
        {
            setError(normalizeApiError(err, 'Could not replace the primary recipient. Please try again.').message);
        }
        finally
        {
            setSubmitting(false);
        }
    };

    return (
        <div className={styles.container}>
            <div className={styles.topBar}>
                <Button
                    id={"replace-primary-recipient-back-btn"}
                    appearance="subtle"
                    shape="circular"
                    icon={<BackIcon/>}
                    onClick={onBack}
                    disabled={submitting}
                >
                    Back
                </Button>
                <Text size={400} weight="semibold">Replace primary recipient</Text>
            </div>

            <Text size={200} className={styles.hint}>
                Verify a new member of a Trusted Organization or choose a published group. The new recipient must sign
                in and accept the Exchange.
            </Text>

            {error && (
                <MessageBar intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            <TrustedOrganizationRecipients
                recipientOrg={recipientOrg}
                recipientOrgGroup={recipientOrgGroup}
                setRecipientOrg={setRecipientOrg}
                setRecipientOrgUser={clearRecipientUser}
                setRecipientOrgGroup={setRecipientOrgGroup}
                setRecipientResolution={setResolution}
            />

            <div className={styles.actions}>
                <Button
                    id={"replace-primary-recipient-confirm-btn"}
                    appearance="primary"
                    shape="circular"
                    disabled={!canSubmit || submitting}
                    onClick={onConfirm}
                >
                    {submitting ? <><Spinner size="tiny"/> Replacing</> : 'Replace recipient'}
                </Button>
                <Button
                    id={"replace-primary-recipient-cancel-btn"}
                    appearance="secondary"
                    shape="circular"
                    onClick={onBack}
                    disabled={submitting}
                >
                    Cancel
                </Button>
            </div>
        </div>
    );
};

export default ReplacePrimaryRecipientPanel;
