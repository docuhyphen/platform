import {Field, Input, Select, Textarea} from "@fluentui/react-components";
import {useSubscriptionTrialTransitionDialog} from "./useSubscriptionTrialTransitionDialog.ts";

interface Props
{
    idPrefix: string;
    fieldGridClassName: string;
    dialog: ReturnType<typeof useSubscriptionTrialTransitionDialog>;
}

const SubscriptionTrialTransitionFields = ({idPrefix, fieldGridClassName, dialog}: Props) => (
    <>
        {dialog.mode === "CONVERT" && (
            <div
                id={`${idPrefix}-conversion-fields`}
                className={fieldGridClassName}>
                <Field
                    id={`${idPrefix}-frequency-field`}
                    label={"Billing frequency"}
                    required>
                    <Select
                        id={`${idPrefix}-frequency-select`}
                        value={dialog.billingFrequency}
                        disabled={dialog.saving}
                        onChange={(_, data) => dialog.setBillingFrequency(data.value as "MONTHLY" | "ANNUAL")}>
                        <option value={"MONTHLY"}>Monthly</option>
                        <option value={"ANNUAL"}>Annual</option>
                    </Select>
                </Field>
                <Field
                    id={`${idPrefix}-period-end-field`}
                    label={"Paid period end"}
                    required>
                    <Input
                        id={`${idPrefix}-period-end-input`}
                        type={"datetime-local"}
                        value={dialog.periodEnd}
                        disabled={dialog.saving}
                        onChange={(_, data) => dialog.setPeriodEnd(data.value)}/>
                </Field>
                {dialog.ownerKind === "organization" && (
                    <Field
                        id={`${idPrefix}-seats-field`}
                        label={"Purchased seats"}
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

export default SubscriptionTrialTransitionFields;
