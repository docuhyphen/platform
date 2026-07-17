import React from "react";
import {Button, Field, Input} from "@fluentui/react-components";
import {useTrustedOrganizationRecipientsStyles} from "./TrustedOrganizationRecipientsStyles.tsx";

interface TrustedPersonEmailResolverProps
{
    email: string;
    loading: boolean;
    error?: string;
    onEmailChange: (email: string) => void;
    onResolve: () => void;
}

const TrustedPersonEmailResolver: React.FC<TrustedPersonEmailResolverProps> = (props) =>
{
    const styles = useTrustedOrganizationRecipientsStyles();
    return <div
        id={"trusted-person-email-resolver"}
        className={styles.resolver}
    >
        <Field
            id={"trusted-person-email-field"}
            label={"Complete work email"}
            validationMessage={props.error}
            validationState={props.error ? "error" : "none"}
        >
            <Input
                id={"trusted-person-email-input"}
                type={"email"}
                value={props.email}
                placeholder={"name@organization.example"}
                onChange={(_, data) => props.onEmailChange(data.value)}
            />
        </Field>
        <Button
            id={"trusted-person-resolve-button"}
            appearance={"primary"}
            shape={"circular"}
            disabled={props.loading || !props.email.trim()}
            onClick={props.onResolve}
        >
            {props.loading ? "Verifying..." : "Verify membership"}
        </Button>
    </div>;
};

export default TrustedPersonEmailResolver;
