import {
    Button,
    Field,
    Input,
    MessageBar,
    MessageBarBody,
    Spinner,
    Textarea,
} from "@fluentui/react-components";
import {AddRegular} from "@fluentui/react-icons";
import OrganizationEntitlementRow from "./OrganizationEntitlementRow.tsx";
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
            <div
                id={"platform-organization-policy-fields"}
                className={styles.policyGrid}>
                <Field
                    id={"platform-organization-tier-code-field"}
                    label={"Tier code"}
                    required>
                    <Input
                        id={"platform-organization-tier-code-input"}
                        value={editor.tierCode}
                        disabled={editor.saving}
                        onChange={(_, data) => editor.setTierCode(data.value.toUpperCase())}/>
                </Field>
                <Field
                    id={"platform-organization-max-users-field"}
                    label={"Licensed capacity"}
                    hint={"Leave blank for unlimited"}>
                    <Input
                        id={"platform-organization-max-users-input"}
                        type={"number"}
                        min={1}
                        value={editor.maxUsers}
                        disabled={editor.saving}
                        onChange={(_, data) => editor.setMaxUsers(data.value)}/>
                </Field>
            </div>
            <Field
                id={"platform-organization-change-reason-field"}
                label={"Change reason"}>
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
