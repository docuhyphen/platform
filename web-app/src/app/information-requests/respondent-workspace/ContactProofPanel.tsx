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
import {useInformationRequestRespondentWorkspaceStyles} from "./InformationRequestRespondentWorkspaceStyles.tsx";

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
    const styles = useInformationRequestRespondentWorkspaceStyles();
    const handleCodeChange = (_event: React.ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
        onCodeChange(data.value.trim());

    return (
        <section id={"information-request-contact-proof"}
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
                    <MessageBarBody>
                        A verification code has been sent.
                    </MessageBarBody>
                </MessageBar>
            )}
            <div id={"information-request-contact-proof-actions"}
                 className={styles.proofActions}>
                <Button id={"information-request-send-code-btn"}
                        shape={"circular"}
                        appearance={"secondary"}
                        icon={busy ? <Spinner size={"tiny"}/> : <SendRegular/>}
                        disabled={busy}
                        onClick={onIssueChallenge}>
                    Send verification code
                </Button>
                <Field label={"Verification code"}>
                    <Input id={"information-request-access-code-input"}
                           value={code}
                           maxLength={6}
                           disabled={busy}
                           onChange={handleCodeChange}
                           onKeyDown={(event) =>
                           {
                               if (event.key === "Enter") onVerify();
                           }}/>
                </Field>
                <Button id={"information-request-verify-code-btn"}
                        shape={"circular"}
                        appearance={"primary"}
                        icon={busy ? <Spinner size={"tiny"}/> : <ShieldCheckmarkRegular/>}
                        disabled={busy || code.length === 0}
                        onClick={onVerify}>
                    Verify
                </Button>
            </div>
        </section>
    );
};

export default ContactProofPanel;
