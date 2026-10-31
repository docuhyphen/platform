import {Button, Field, Input, InputOnChangeData, Spinner} from "@fluentui/react-components";
import {SendRegular, ShieldCheckmarkRegular} from "@fluentui/react-icons";
import {useStepUpCodeEntryStyles} from "./StepUpCodeEntryStyles.tsx";

interface Props {
    code: string;
    busy: boolean;
    onCodeChange: (value: string) => void;
    onSendCode: () => void;
    onVerify: () => void;
}

const StepUpCodeEntry = ({code, busy, onCodeChange, onSendCode, onVerify}: Props) => {
    const styles = useStepUpCodeEntryStyles();
    const handleCodeChange = (_event: React.ChangeEvent<HTMLInputElement>, data: InputOnChangeData) =>
        onCodeChange(data.value.trim());
    const verifyIcon = busy
        ? (
            <Spinner id={"information-request-step-up-verify-spinner"}
                     size={"tiny"}/>
        )
        : <ShieldCheckmarkRegular/>;

    return (
        <div id={"information-request-step-up-code-entry"}
             className={styles.codeEntry}>
            <Button id={"information-request-step-up-send-code-btn"}
                    shape={"circular"}
                    appearance={"secondary"}
                    icon={<SendRegular/>}
                    disabled={busy}
                    onClick={onSendCode}>
                Send verification code
            </Button>
            <Field id={"information-request-step-up-code-field"}
                   label={"Verification code"}
                   className={styles.codeField}>
                <Input id={"information-request-step-up-code-input"}
                       value={code}
                       maxLength={6}
                       disabled={busy}
                       onChange={handleCodeChange}
                       onKeyDown={(event) => {
                           if (event.key === "Enter" && code.length > 0) onVerify();
                       }}/>
            </Field>
            <Button id={"information-request-step-up-verify-btn"}
                    shape={"circular"}
                    appearance={"primary"}
                    icon={verifyIcon}
                    disabled={busy || code.length === 0}
                    onClick={onVerify}>
                Verify
            </Button>
        </div>
    );
};

export default StepUpCodeEntry;
