import {Field, Input, Textarea} from "@fluentui/react-components";
import {SubscriptionTrialRequest} from "../../../../services/types/subscriptionTrialRequests.ts";

interface Props
{
    approving: boolean;
    request: SubscriptionTrialRequest | null;
    durationDays: string;
    seatCapacity: string;
    reason: string;
    fieldsClassName: string;
    setDurationDays: (value: string) => void;
    setSeatCapacity: (value: string) => void;
    setReason: (value: string) => void;
}

const TrialRequestDecisionFields = (props: Props) => (
    <>
        {props.approving && (
            <div
                id={"platform-trial-request-approval-fields"}
                className={props.fieldsClassName}>
                <Field
                    id={"platform-trial-request-duration-field"}
                    label={"Duration in days"}
                    required>
                    <Input
                        id={"platform-trial-request-duration"}
                        type={"number"}
                        min={1}
                        value={props.durationDays}
                        onChange={(_, data) => props.setDurationDays(data.value)}/>
                </Field>
                {props.request?.ownerType === "ORGANIZATION" && (
                    <Field
                        id={"platform-trial-request-seats-field"}
                        label={"Trial seats"}
                        required>
                        <Input
                            id={"platform-trial-request-seats"}
                            type={"number"}
                            min={1}
                            value={props.seatCapacity}
                            onChange={(_, data) => props.setSeatCapacity(data.value)}/>
                    </Field>
                )}
            </div>
        )}
        <Field
            id={"platform-trial-request-decision-reason-field"}
            label={"Decision reason"}
            required>
            <Textarea
                id={"platform-trial-request-decision-reason"}
                value={props.reason}
                maxLength={1024}
                resize={"vertical"}
                onChange={(_, data) => props.setReason(data.value)}/>
        </Field>
    </>
);

export default TrialRequestDecisionFields;
