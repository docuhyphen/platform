import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {
    createSubscriptionTrialRequest,
    fetchCurrentSubscriptionTrialRequest,
    SubscriptionTrialRequestApiError,
} from "../../../../services/subscriptionTrialRequestApi.ts";
import {CurrentSubscriptionTrialRequest} from "../../../../services/types/subscriptionTrialRequests.ts";
import {useBillingTrialRequestActionStyles} from "./BillingTrialRequestActionStyles.tsx";

interface Props
{
    ownerType: "USER" | "ORGANIZATION";
}

const BillingTrialRequestAction = ({ownerType}: Props) =>
{
    const styles = useBillingTrialRequestActionStyles();
    const [state, setState] = useState<CurrentSubscriptionTrialRequest | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");

    useEffect(() =>
    {
        let active = true;
        setLoading(true);
        void fetchCurrentSubscriptionTrialRequest()
            .then(result => active && setState(result))
            .catch((failure: SubscriptionTrialRequestApiError) => active && setError(failure.errorMessage))
            .finally(() => active && setLoading(false));
        return () => { active = false; };
    }, [ownerType]);

    const submit = async () =>
    {
        setSaving(true);
        setError("");
        try
        {
            const request = await createSubscriptionTrialRequest();
            setState({eligible: false, ineligibilityReason: "A trial request is awaiting review", request});
        }
        catch (failure: unknown)
        {
            setError((failure as SubscriptionTrialRequestApiError).errorMessage);
        }
        finally
        {
            setSaving(false);
        }
    };

    if (loading) return <Spinner id={"settings-billing-trial-request-loading"} size={"tiny"}/>;
    const pending = state?.request?.status === "PENDING";
    const targetPlan = ownerType === "USER" ? "Personal" : "Business";
    return (
        <div
            id={"settings-billing-trial-request-action"}
            className={styles.container}>
            {error && (
                <MessageBar
                    id={"settings-billing-trial-request-error"}
                    className={styles.feedback}
                    intent={"error"}>
                    <MessageBarBody id={"settings-billing-trial-request-error-body"}>{error}</MessageBarBody>
                </MessageBar>
            )}
            {pending && (
                <MessageBar
                    id={"settings-billing-trial-request-pending"}
                    className={styles.feedback}
                    intent={"info"}>
                    <MessageBarBody id={"settings-billing-trial-request-pending-body"}>
                        Your {targetPlan} trial request is awaiting App Administrator review.
                    </MessageBarBody>
                </MessageBar>
            )}
            {state?.request?.status === "REJECTED" && state.request.decisionReason && (
                <Text id={"settings-billing-trial-request-decision"}>
                    Previous request decision: {state.request.decisionReason}
                </Text>
            )}
            {state?.eligible && (
                <Button
                    id={"settings-billing-request-trial-button"}
                    appearance={"primary"}
                    shape={"circular"}
                    disabled={saving}
                    onClick={() => void submit()}>
                    {saving ? <Spinner id={"settings-billing-trial-request-saving"} size={"tiny"}/>
                        : `Request ${targetPlan} trial`}
                </Button>
            )}
            {!state?.eligible && !pending && state?.ineligibilityReason && (
                <Text id={"settings-billing-trial-request-ineligible"}>{state.ineligibilityReason}</Text>
            )}
        </div>
    );
};

export default BillingTrialRequestAction;
