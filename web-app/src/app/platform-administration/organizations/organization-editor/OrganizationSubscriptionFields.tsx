import {Field, Input, Select} from "@fluentui/react-components";
import {useOrganizationEditorStyles} from "./OrganizationEditorStyles.tsx";
import {useOrganizationEditor} from "./useOrganizationEditor.ts";

interface OrganizationSubscriptionFieldsProps
{
    editor: ReturnType<typeof useOrganizationEditor>;
}

const OrganizationSubscriptionFields = ({editor}: OrganizationSubscriptionFieldsProps) =>
{
    const styles = useOrganizationEditorStyles();
    return (
        <div
            id={"platform-organization-subscription-fields"}
            className={styles.policyGrid}>
            <Field
                id={"platform-organization-tier-code-field"}
                label={"Tier code"}
                required>
                <Select
                    id={"platform-organization-tier-code-select"}
                    value={editor.tierCode}
                    disabled={editor.saving}
                    onChange={(_, data) => editor.setTierCode(data.value)}>
                    <option
                        id={"platform-organization-tier-code-business"}
                        value={"BUSINESS"}>
                        Business
                    </option>
                </Select>
            </Field>
            <Field
                id={"platform-organization-max-users-field"}
                label={"Purchased seats"}
                hint={"Leave blank when purchased capacity has not been assigned"}>
                <Input
                    id={"platform-organization-max-users-input"}
                    type={"number"}
                    min={1}
                    value={editor.maxUsers}
                    disabled={editor.saving}
                    onChange={(_, data) => editor.setMaxUsers(data.value)}/>
            </Field>
            <Field
                id={"platform-organization-subscription-status-field"}
                label={"Subscription status"}>
                <Select
                    id={"platform-organization-subscription-status-select"}
                    value={editor.subscriptionStatus}
                    disabled={editor.saving}
                    onChange={(_, data) => editor.setSubscriptionStatus(data.value)}>
                    {(["TRIALING", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELED"] as const)
                        .map((status) => <option key={status} value={status}>{status}</option>)}
                </Select>
            </Field>
            <Field
                id={"platform-organization-billing-frequency-field"}
                label={"Billing frequency"}>
                <Select
                    id={"platform-organization-billing-frequency-select"}
                    value={editor.billingFrequency}
                    disabled={editor.saving}
                    onChange={(_, data) => editor.setBillingFrequency(data.value)}>
                    <option value={""}>Not set</option>
                    <option value={"MONTHLY"}>Monthly</option>
                    <option value={"ANNUAL"}>Annual</option>
                </Select>
            </Field>
            <Field
                id={"platform-organization-period-start-field"}
                label={"Current period start"}>
                <Input
                    id={"platform-organization-period-start-input"}
                    type={"datetime-local"}
                    value={editor.currentPeriodStart}
                    disabled={editor.saving}
                    onChange={(_, data) => editor.setCurrentPeriodStart(data.value)}/>
            </Field>
            <Field
                id={"platform-organization-period-end-field"}
                label={"Current period end"}>
                <Input
                    id={"platform-organization-period-end-input"}
                    type={"datetime-local"}
                    value={editor.currentPeriodEnd}
                    disabled={editor.saving}
                    onChange={(_, data) => editor.setCurrentPeriodEnd(data.value)}/>
            </Field>
            {editor.subscriptionStatus === "PAST_DUE" && (
                <Field
                    id={"platform-organization-grace-period-end-field"}
                    label={"Grace period end"}>
                    <Input
                        id={"platform-organization-grace-period-end-input"}
                        type={"datetime-local"}
                        value={editor.gracePeriodEnd}
                        disabled={editor.saving}
                        onChange={(_, data) => editor.setGracePeriodEnd(data.value)}/>
                </Field>
            )}
        </div>
    );
};

export default OrganizationSubscriptionFields;
