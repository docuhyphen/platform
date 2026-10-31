import {MessageBar, MessageBarBody} from "@fluentui/react-components";

interface Props
{
    accessCodeStatus: string;
    invitationStatus: string;
    accessCodeError: string;
    invitationError: string;
}

const ExchangeSettingsStatusMessages = ({accessCodeStatus, invitationStatus, accessCodeError, invitationError}: Props) =>
    (
        <>
            {accessCodeStatus && (
                <MessageBar id={"access-mgmt-access-code-status"}
                            intent={"success"}>
                    <MessageBarBody id={"access-mgmt-access-code-status-body"}>{accessCodeStatus}</MessageBarBody>
                </MessageBar>
            )}
            {invitationStatus && (
                <MessageBar id={"access-mgmt-invitation-status"}
                            intent={"success"}>
                    <MessageBarBody id={"access-mgmt-invitation-status-body"}>{invitationStatus}</MessageBarBody>
                </MessageBar>
            )}
            {accessCodeError && (
                <MessageBar id={"access-mgmt-access-code-error"}
                            intent={"error"}>
                    <MessageBarBody id={"access-mgmt-access-code-error-body"}>{accessCodeError}</MessageBarBody>
                </MessageBar>
            )}
            {invitationError && (
                <MessageBar id={"access-mgmt-invitation-error"}
                            intent={"error"}>
                    <MessageBarBody id={"access-mgmt-invitation-error-body"}>{invitationError}</MessageBarBody>
                </MessageBar>
            )}
        </>
    );

export default ExchangeSettingsStatusMessages;
