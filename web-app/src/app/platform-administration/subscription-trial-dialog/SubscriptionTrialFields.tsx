import {Field, Input, Textarea} from "@fluentui/react-components";
import {useSubscriptionTrialDialog} from "./useSubscriptionTrialDialog.ts";

interface SubscriptionTrialFieldsProps
{
    idPrefix: string;
    fieldGridClassName: string;
    dialog: ReturnType<typeof useSubscriptionTrialDialog>;
}

const SubscriptionTrialFields = ({
    idPrefix,
    fieldGridClassName,
    dialog,
}: SubscriptionTrialFieldsProps) => (
    <>
        {dialog.isExtension ? (
            <Field
                id={`${idPrefix}-end-field`}
                label={"New trial end"}
                required>
                <Input
                    id={`${idPrefix}-end-input`}
                    type={"datetime-local"}
                    value={dialog.extensionEnd}
                    disabled={dialog.saving}
                    onChange={(_, data) => dialog.setExtensionEnd(data.value)}/>
            </Field>
        ) : (
            <div
                id={`${idPrefix}-start-fields`}
                className={fieldGridClassName}>
                <Field
                    id={`${idPrefix}-duration-field`}
                    label={"Duration in days"}
                    required>
                    <Input
                        id={`${idPrefix}-duration-input`}
                        type={"number"}
                        min={1}
                        value={dialog.durationDays}
                        disabled={dialog.saving}
                        onChange={(_, data) => dialog.setDurationDays(data.value)}/>
                </Field>
                {dialog.ownerKind === "organization" && (
                    <Field
                        id={`${idPrefix}-seats-field`}
                        label={"Trial seats"}
                        required>
                        <Input
                            id={`${idPrefix}-seats-input`}
                            type={"number"}
                            min={1}
                            value={dialog.seatCapacity}
                            disabled={dialog.saving}
                            onChange={(_, data) => dialog.setSeatCapacity(data.value)}/>
                    </Field>
                )}
            </div>
        )}
        <Field
            id={`${idPrefix}-reason-field`}
            label={"Reason"}
            required>
            <Textarea
                id={`${idPrefix}-reason-input`}
                value={dialog.reason}
                disabled={dialog.saving}
                maxLength={1024}
                resize={"vertical"}
                onChange={(_, data) => dialog.setReason(data.value)}/>
        </Field>
    </>
);

export default SubscriptionTrialFields;
