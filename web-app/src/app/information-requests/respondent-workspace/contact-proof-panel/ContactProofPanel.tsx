import {
    Button,
    Field,
    Input,
    InputOnChangeData,
    MessageBar,
    MessageBarBody,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {SendRegular, ShieldCheckmarkRegular} from "@fluentui/react-icons";
import {useContactProofPanelStyles} from "./ContactProofPanelStyles.tsx";

interface Props
{
    code: string;
    challengeSent: boolean;
    busy: boolean;
    onCodeChange: (value: string) => void;
    onIssueChallenge: () => void;
    onVerify: () => void;
}

const ContactProofPanel = ({
                               code,
                               challengeSent,
                               busy,
                               onCodeChange,
                               onIssueChallenge,
                               onVerify,
                           }: Props) =>
{
    const styles = useContactProofPanelStyles();
    const handleCodeChange = (_event: React.ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
        onCodeChange(data.value.trim());
    const sendIcon = busy
        ? (
            <Spinner id={"information-request-send-code-spinner"}
                     size={"tiny"}/>
        )
        : <SendRegular/>;
    const verifyIcon = busy
        ? (
            <Spinner id={"information-request-verify-code-spinner"}
                     size={"tiny"}/>
        )
        : <ShieldCheckmarkRegular/>;

    return (
        <section id={"information-request-contact-proof"}
                 aria-labelledby={"information-request-contact-proof-title"}
                 className={styles.proofPanel}>
            <Text id={"information-request-contact-proof-title"}
                  size={500}
                  weight={"semibold"}>
                Verify access
            </Text>
            <Text id={"information-request-contact-proof-copy"}
                  className={styles.mutedText}>
                Enter the verification code for this Information Request.
            </Text>
            {challengeSent && (
                <MessageBar id={"information-request-contact-proof-sent"}
                            intent={"success"}>
                    <MessageBarBody id={"information-request-contact-proof-sent-body"}>
                        A verification code has been sent.
                    </MessageBarBody>
                </MessageBar>
            )}
            <div id={"information-request-contact-proof-actions"}
                 className={styles.proofActions}>
                <Button id={"information-request-send-code-btn"}
                        shape={"circular"}
                        appearance={"secondary"}
                        icon={sendIcon}
                        disabled={busy}
                        onClick={onIssueChallenge}>
                    Send verification code
                </Button>
                <Field id={"information-request-access-code-field"}
                       label={"Verification code"}>
                    <Input id={"information-request-access-code-input"}
                           value={code}
                           maxLength={6}
                           disabled={busy}
                           onChange={handleCodeChange}
                           onKeyDown={(event) =>
                           {
                               if (event.key === "Enter" && code.length > 0) onVerify();
                           }}/>
                </Field>
                <Button id={"information-request-verify-code-btn"}
                        shape={"circular"}
                        appearance={"primary"}
                        icon={verifyIcon}
                        disabled={busy || code.length === 0}
                        onClick={onVerify}>
                    Verify
                </Button>
            </div>
        </section>
    );
};

export default ContactProofPanel;
