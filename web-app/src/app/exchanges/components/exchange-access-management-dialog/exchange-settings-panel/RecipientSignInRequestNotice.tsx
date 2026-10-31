import {MessageBar, MessageBarBody} from "@fluentui/react-components";

const RecipientSignInRequestNotice = () => (
    <MessageBar id={"access-mgmt-sign-in-request-notice"}
                intent={"warning"}>
        <MessageBarBody id={"access-mgmt-sign-in-request-notice-body"}>
            Access links on this Exchange&apos;s requests stop working. Someone added to a request by email who has
            not linked an account can respond again only after you assign their part to a recipient of this Exchange.
        </MessageBarBody>
    </MessageBar>
);

export default RecipientSignInRequestNotice;
