import {Field, Input, Select, Textarea} from "@fluentui/react-components";
import {PlatformUserSubscriptionPolicy} from "../../../services/types/platformUserSubscriptions.ts";
import {useUserSubscriptionsStyles} from "./UserSubscriptionsStyles.tsx";

export interface UserSubscriptionFieldValues
{
    planCode: PlatformUserSubscriptionPolicy["planCode"];
    status: PlatformUserSubscriptionPolicy["subscriptionStatus"];
    billingFrequency: string;
    periodStart: string;
    periodEnd: string;
    graceEnd: string;
    reason: string;
}

interface UserSubscriptionFieldsProps
{
    values: UserSubscriptionFieldValues;
    disabled: boolean;
    onChange: <K extends keyof UserSubscriptionFieldValues>(key: K, value: UserSubscriptionFieldValues[K]) => void;
}

const UserSubscriptionFields = ({values, disabled, onChange}: UserSubscriptionFieldsProps) =>
{
    const styles = useUserSubscriptionsStyles();
    return (
        <>
            <div
                id={"platform-user-subscription-field-grid"}
                className={styles.grid}>
                <Field
                    id={"platform-user-subscription-plan-field"}
                    label={"Plan"}>
                    <Select
                        id={"platform-user-subscription-plan-select"}
                        value={values.planCode}
                        disabled={disabled}
                        onChange={(_, data) => onChange("planCode", data.value as UserSubscriptionFieldValues["planCode"])}>
                        <option value={"FREE"}>Free</option>
                        <option value={"PERSONAL"}>Personal</option>
                    </Select>
                </Field>
                <Field
                    id={"platform-user-subscription-status-field"}
                    label={"Status"}>
                    <Select
                        id={"platform-user-subscription-status-select"}
                        value={values.status}
                        disabled={disabled}
                        onChange={(_, data) => onChange("status", data.value as UserSubscriptionFieldValues["status"])}>
                        {(["TRIALING", "ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELED"] as const)
                            .map((status) => <option key={status} value={status}>{status}</option>)}
                    </Select>
                </Field>
                <Field
                    id={"platform-user-subscription-frequency-field"}
                    label={"Billing frequency"}>
                    <Select
                        id={"platform-user-subscription-frequency-select"}
                        value={values.billingFrequency}
                        disabled={disabled}
                        onChange={(_, data) => onChange("billingFrequency", data.value)}>
                        <option value={""}>Not set</option>
                        <option value={"MONTHLY"}>Monthly</option>
                        <option value={"ANNUAL"}>Annual</option>
                    </Select>
                </Field>
                <Field
                    id={"platform-user-subscription-period-start-field"}
                    label={"Current period start"}>
                    <Input
                        id={"platform-user-subscription-period-start-input"}
                        type={"datetime-local"}
                        value={values.periodStart}
                        disabled={disabled}
                        onChange={(_, data) => onChange("periodStart", data.value)}/>
                </Field>
                <Field
                    id={"platform-user-subscription-period-end-field"}
                    label={"Current period end"}>
                    <Input
                        id={"platform-user-subscription-period-end-input"}
                        type={"datetime-local"}
                        value={values.periodEnd}
                        disabled={disabled}
                        onChange={(_, data) => onChange("periodEnd", data.value)}/>
                </Field>
                {values.status === "PAST_DUE" && (
                    <Field
                        id={"platform-user-subscription-grace-end-field"}
                        label={"Grace period end"}>
                        <Input
                            id={"platform-user-subscription-grace-end-input"}
                            type={"datetime-local"}
                            value={values.graceEnd}
                            disabled={disabled}
                            onChange={(_, data) => onChange("graceEnd", data.value)}/>
                    </Field>
                )}
            </div>
            <Field
                id={"platform-user-subscription-reason-field"}
                label={"Change reason"}
                required>
                <Textarea
                    id={"platform-user-subscription-reason-input"}
                    value={values.reason}
                    disabled={disabled}
                    maxLength={1024}
                    resize={"vertical"}
                    onChange={(_, data) => onChange("reason", data.value)}/>
            </Field>
        </>
    );
};

export default UserSubscriptionFields;

