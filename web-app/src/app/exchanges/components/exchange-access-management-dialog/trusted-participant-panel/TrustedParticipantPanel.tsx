import {useCallback, useState} from 'react';
import {Button, Combobox, Field, MessageBar, MessageBarBody, Option, Spinner, Text} from '@fluentui/react-components';
import {OrganizationBasicDto} from '../../../../models/models.tsx';
import {OrganizationGroupBasicDto} from '../../../../../services/organizationApi.ts';
import {ExternalIdentityResolution} from '../../../../../services/organizationTrust.ts';
import TrustedOrganizationRecipients from '../../../../exchange-initiation/components/exchange-initiation-recipients-tab/trusted-organization-recipients/TrustedOrganizationRecipients.tsx';
import {buildRecipientSelection} from '../../../../exchange-initiation/exchangeInitiationRecipientSelection.ts';
import {ExchangeInitiationRecipientMode} from '../../../../exchange-initiation/components/exchange-initiation-recipients-tab/exchangeInitiationRecipientMode.ts';
import {inviteTrustedParticipant} from '../../../../../services/exchangeApi.ts';
import {AssignableRoleDisplayNames, ExchangeShareRoleName} from '../../../../../services/types/roles.ts';
import {normalizeApiError} from '../../../../../utils/apiErrorUtils.ts';
import {useTrustedParticipantPanelStyles} from './TrustedParticipantPanelStyles.tsx';

interface TrustedParticipantPanelProps
{
    exchangeId: string;
    onCancel: () => void;
    onInvited: () => void;
}
const TrustedParticipantPanel = ({exchangeId, onCancel, onInvited}: TrustedParticipantPanelProps) =>
{
    const styles = useTrustedParticipantPanelStyles();
    const [organization, setOrganization] = useState<OrganizationBasicDto>();
    const [group, setGroup] = useState<OrganizationGroupBasicDto>();
    const [resolution, setResolution] = useState<ExternalIdentityResolution>();
    const [role, setRole] = useState<ExchangeShareRoleName>(ExchangeShareRoleName.VIEWER);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const canSubmit = Boolean(resolution?.id) || Boolean(group?.id);
    const clearRecipientUser = useCallback(() => undefined, []);
    const invite = async () =>
    {
        if (!canSubmit || submitting) return;
        setSubmitting(true);
        setError(null);
        try
        {
            const selection = buildRecipientSelection({
                mode: ExchangeInitiationRecipientMode.TRUSTED_ORG,
                organization,
                group,
                resolutionId: resolution?.id,
            });
            await inviteTrustedParticipant(exchangeId, {selection, roleName: role});
            onInvited();
        }
        catch (caught: unknown)
        {
            setError(normalizeApiError(
                caught,
                'Could not invite the trusted participant. Please try again.',
            ).message);
        }
        finally
        {
            setSubmitting(false);
        }
    };
    return (
        <div
            id={"trusted-participant-form"}
            className={styles.form}
        >
            <Text
                id={"trusted-participant-hint"}
                size={200}
                className={styles.hint}
            >
                Verify a member or choose a published group. Access remains inactive until the participant signs in
                and accepts or declines the separate access invitation. This does not accept or reject the Exchange.
            </Text>
            {error && (
                <MessageBar
                    id={"trusted-participant-error"}
                    intent="error"
                >
                    <MessageBarBody id={"trusted-participant-error-message"}>{error}</MessageBarBody>
                </MessageBar>
            )}
            <TrustedOrganizationRecipients
                recipientOrg={organization}
                recipientOrgGroup={group}
                setRecipientOrg={setOrganization}
                setRecipientOrgUser={clearRecipientUser}
                setRecipientOrgGroup={setGroup}
                setRecipientResolution={setResolution}
            />
            <Field
                id={"trusted-participant-role-field"}
                label="Access role"
                className={styles.role}
            >
                <Combobox
                    id={"trusted-participant-role"}
                    value={AssignableRoleDisplayNames[role] ?? role}
                    selectedOptions={[role]}
                    disabled={submitting}
                    onOptionSelect={(_event, data) => setRole(
                        (data.optionValue as ExchangeShareRoleName | undefined)
                        ?? ExchangeShareRoleName.VIEWER,
                    )}
                >
                    {Object.entries(AssignableRoleDisplayNames).map(([value, label]) => (
                        <Option
                            id={`trusted-participant-role-${value.toLowerCase()}`}
                            key={value}
                            value={value}
                        >
                            {label}
                        </Option>
                    ))}
                </Combobox>
            </Field>
            <div
                id={"trusted-participant-actions"}
                className={styles.actions}
            >
                <Button
                    id={"trusted-participant-invite-btn"}
                    appearance="primary"
                    shape="circular"
                    disabled={!canSubmit || submitting}
                    onClick={invite}
                >
                    {submitting ? (
                        <>
                            <Spinner
                                id={"trusted-participant-invite-spinner"}
                                size="tiny"
                            />
                            Inviting
                        </>
                    ) : 'Invite participant'}
                </Button>
                <Button
                    id={"trusted-participant-cancel-btn"}
                    appearance="secondary"
                    shape="circular"
                    disabled={submitting}
                    onClick={onCancel}
                >
                    Cancel
                </Button>
            </div>
        </div>
    );
};

export default TrustedParticipantPanel;
