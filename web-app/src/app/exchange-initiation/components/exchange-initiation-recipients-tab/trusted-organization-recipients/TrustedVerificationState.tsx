import React from "react";
import {Badge, Text} from "@fluentui/react-components";
import {useTrustedOrganizationRecipientsStyles} from "./TrustedOrganizationRecipientsStyles.tsx";

interface TrustedVerificationStateProps
{
    organizationName: string;
    expired: boolean;
}

const TrustedVerificationState: React.FC<TrustedVerificationStateProps> = (props) =>
{
    const styles = useTrustedOrganizationRecipientsStyles();
    return <div
        id={"trusted-person-verification-state"}
        className={styles.verificationState}
    >
        <Badge
            id={"trusted-person-verification-badge"}
            appearance={"outline"}
            color={props.expired ? "warning" : "success"}
        >
            {props.expired ? "Verification expired" : `Membership verified by ${props.organizationName}`}
        </Badge>
        {props.expired && <Text id={"trusted-person-expired-guidance"}>
            Verify the exact email again before continuing.
        </Text>}
    </div>;
};

export default TrustedVerificationState;
