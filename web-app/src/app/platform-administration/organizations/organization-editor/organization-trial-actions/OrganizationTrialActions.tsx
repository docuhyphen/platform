import {Button} from "@fluentui/react-components";
import {useOrganizationTrialActionsStyles} from "./OrganizationTrialActionsStyles.tsx";

interface Props
{
    subscriptionStatus: string;
    billingFrequency: string;
    currentPeriodEnd: string;
    disabled: boolean;
    onManageTrial: () => void;
    onEndTrial: () => void;
    onConvertTrial: () => void;
}

const OrganizationTrialActions = ({
    subscriptionStatus,
    billingFrequency,
    currentPeriodEnd,
    disabled,
    onManageTrial,
    onEndTrial,
    onConvertTrial,
}: Props) =>
{
    const styles = useOrganizationTrialActionsStyles();
    const activeTrial = subscriptionStatus === "TRIALING"
        && currentPeriodEnd !== ""
        && new Date(currentPeriodEnd).getTime() > Date.now();
    const canStartTrial = billingFrequency === ""
        && (subscriptionStatus === "ACTIVE" || subscriptionStatus === "TRIALING");
    return (
        <div
            id={"platform-organization-trial-actions"}
            className={styles.actions}>
            <Button
                id={"platform-organization-manage-trial"}
                appearance={"secondary"}
                shape={"circular"}
                disabled={disabled || (!activeTrial && !canStartTrial)}
                onClick={onManageTrial}>
                {activeTrial ? "Extend Business trial" : "Start Business trial"}
            </Button>
            {activeTrial && (
                <Button
                    id={"platform-organization-end-trial"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={disabled}
                    onClick={onEndTrial}>
                    End Business trial
                </Button>
            )}
            {subscriptionStatus === "TRIALING" && (
                <Button
                    id={"platform-organization-convert-trial"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={disabled}
                    onClick={onConvertTrial}>
                    Convert to paid
                </Button>
            )}
        </div>
    );
};

export default OrganizationTrialActions;
