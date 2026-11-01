import {Button, MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {LinkRegular, OpenRegular} from "@fluentui/react-icons";
import {useAccountLinkOfferStyles} from "./AccountLinkOfferStyles.tsx";
import {useParticipantAccountLink} from "./useParticipantAccountLink.ts";
import {PendingAccountLink} from "./usePendingAccountLink.ts";

interface Props {
    requestId: string;
    accessLinkToken: string;
    sessionId: string;
    respondentKey: string;
    pending: PendingAccountLink;
    onAccessSessionEnded?: (refusal: unknown) => void;
}

const offerCopy = (signedInEmail: string | null, recovering: boolean): string => {
    if (!signedInEmail) {
        return "Have an account with the email address this request was sent to? Sign in opens in a new tab; once you "
            + "have signed in there, come back here to link this request to your account.";
    }
    if (recovering) {
        return `Your verified session ended while this request was being linked to ${signedInEmail}. Select Link to `
            + "my account to finish linking. If the link was not made, verify your access again.";
    }
    return `Link this request to the account you are signed in with, ${signedInEmail}. You then answer it `
        + "from your account, and this access link stops working.";
};

const AccountLinkOffer = ({
                              requestId,
                              accessLinkToken,
                              sessionId,
                              respondentKey,
                              pending,
                              onAccessSessionEnded
                          }: Props) => {
    const styles = useAccountLinkOfferStyles();
    const state = useParticipantAccountLink(requestId, accessLinkToken, sessionId, respondentKey, pending, onAccessSessionEnded);

    if (!state.offered) return null;

    return (
        <section id={"information-request-account-link"}
                 aria-labelledby={"information-request-account-link-title"}
                 className={styles.offer}>
            <Text id={"information-request-account-link-title"}
                  as={"h2"}
                  size={400}
                  weight={"semibold"}>
                Use your account for this request
            </Text>
            <Text id={"information-request-account-link-copy"}
                  className={styles.copy}>
                {offerCopy(state.signedInEmail, state.recovering)}
            </Text>
            <div id={"information-request-account-link-actions"}
                 className={styles.actions}>
                {state.signedInEmail
                    ? (
                        <Button id={"information-request-account-link-button"}
                                appearance={"primary"}
                                shape={"circular"}
                                icon={<LinkRegular/>}
                                disabled={state.busy}
                                onClick={() => { void state.link(); }}>
                            Link to my account
                        </Button>
                    )
                    : (
                        <>
                            <Button id={"information-request-account-link-sign-in"}
                                    as={"a"}
                                    href={"/sign-in"}
                                    target={"_blank"}
                                    rel={"noopener noreferrer"}
                                    appearance={"secondary"}
                                    shape={"circular"}
                                    icon={<OpenRegular/>}>
                                Sign in
                            </Button>
                            <Button id={"information-request-account-link-continue"}
                                    appearance={"subtle"}
                                    shape={"circular"}
                                    disabled={state.busy}
                                    onClick={() => { void state.checkSignIn(); }}>
                                I have signed in
                            </Button>
                        </>
                    )}
            </div>
            {state.error && (
                <MessageBar id={"information-request-account-link-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-account-link-error-body"}>
                        {state.error}
                    </MessageBarBody>
                </MessageBar>
            )}
        </section>
    );
};

export default AccountLinkOffer;
