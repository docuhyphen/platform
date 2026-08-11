import {
    Button,
    Caption1,
    Checkbox,
    Field,
    MessageBar,
    MessageBarBody,
    Spinner,
    Textarea,
} from "@fluentui/react-components";
import {AddRegular} from "@fluentui/react-icons";
import OrganizationEntitlementRow from "./OrganizationEntitlementRow.tsx";
import OrganizationSubscriptionFields from "./OrganizationSubscriptionFields.tsx";
import {useOrganizationEditorStyles} from "./OrganizationEditorStyles.tsx";
import {useOrganizationEditor} from "./useOrganizationEditor.ts";

interface OrganizationEditorFormProps
{
    editor: ReturnType<typeof useOrganizationEditor>;
}

const OrganizationEditorForm = ({editor}: OrganizationEditorFormProps) =>
{
    const styles = useOrganizationEditorStyles();

    return (
        <>
            {editor.error && (
                <MessageBar
                    id={"platform-organization-editor-error"}
                    intent={"error"}>
                    <MessageBarBody id={"platform-organization-editor-error-body"}>
                        {editor.error}
                    </MessageBarBody>
                </MessageBar>
            )}
            <OrganizationSubscriptionFields editor={editor}/>
            <div
                id={"platform-organization-seat-usage"}
                className={styles.seatUsage}>
                <Caption1 id={"platform-organization-purchased-seats"}>
                    Purchased seats: {editor.maxUsers || "Unassigned"}
                </Caption1>
                <Caption1 id={"platform-organization-active-seats"}>
                    Active seats: {editor.activeSeats}
                </Caption1>
                <Caption1 id={"platform-organization-remaining-seats"}>
                    Remaining seats: {editor.remainingSeats ?? "Uncapped"}
                </Caption1>
            </div>
            <div
                id={"platform-organization-status-fields"}
                className={styles.statusOptions}>
                <Checkbox
                    id={"platform-organization-active-checkbox"}
                    label={"Active"}
                    checked={editor.active}
                    disabled={editor.saving}
                    onChange={(_, data) => editor.setActive(data.checked === true)}/>
                <Checkbox
                    id={"platform-organization-verified-checkbox"}
                    label={"Verified"}
                    checked={editor.verificationComplete}
                    disabled={editor.saving}
                    onChange={(_, data) => editor.setVerificationComplete(data.checked === true)}/>
            </div>
            <Field
                id={"platform-organization-change-reason-field"}
                label={"Change reason"}
                required>
                <Textarea
                    id={"platform-organization-change-reason-input"}
                    value={editor.changeReason}
                    disabled={editor.saving}
                    maxLength={1024}
                    resize={"vertical"}
                    onChange={(_, data) => editor.setChangeReason(data.value)}/>
            </Field>
            <div
                id={"platform-organization-entitlements"}
                className={styles.entitlements}>
                {editor.entitlements.map((entitlement, index) => (
                    <OrganizationEntitlementRow
                        key={index}
                        index={index}
                        entitlement={entitlement}
                        disabled={editor.saving}
                        onChange={editor.updateEntitlement}
                        onRemove={editor.removeEntitlement}/>
                ))}
                <Button
                    id={"platform-organization-add-entitlement"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<AddRegular/>}
                    disabled={editor.saving || editor.entitlements.length >= 100}
                    onClick={editor.addEntitlement}>
                    Add feature
                </Button>
            </div>
        </>
    );
};

export const OrganizationEditorSaveContent = ({saving}: {saving: boolean}) =>
    saving ? <Spinner size={"tiny"}/> : <>Save</>;

export default OrganizationEditorForm;
