import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {ShieldCheckmarkRegular} from "@fluentui/react-icons";
import StepUpCodeEntry from "./step-up-code-entry/StepUpCodeEntry.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {useStepUpPanelStyles} from "./StepUpPanelStyles.tsx";
import {useStepUpVerification} from "./useStepUpVerification.ts";

interface Props {
    requestId: string;
    awaiting: number;
    accessLinkToken?: string;
    onVerified: () => void;
    onAccessSessionEnded?: (refusal: unknown) => void;
}

const hiddenQuestions = (awaiting: number): string =>
    awaiting === 1
        ? "1 protected question is hidden until you verify again."
        : `${awaiting} protected questions are hidden until you verify again.`;

const openUntil = (verifiedUntil: number): string => {
    return `Protected questions stay open until ${formatInformationRequestTime(verifiedUntil)}. ` +
        "After that, you will be asked to verify again.";
};

const StepUpPanel = ({requestId, awaiting, accessLinkToken, onVerified, onAccessSessionEnded}: Props) => {
    const styles = useStepUpPanelStyles();
    const stepUp = useStepUpVerification(requestId, accessLinkToken, onVerified, onAccessSessionEnded);
    const accountIcon = stepUp.busy
        ? (
            <Spinner id={"information-request-step-up-account-spinner"}
                     size={"tiny"}/>
        )
        : <ShieldCheckmarkRegular/>;

    if (awaiting === 0) {
        return stepUp.verifiedUntil
            ? (
                <MessageBar id={"information-request-step-up-open"}
                            intent={"success"}>
                    <MessageBarBody id={"information-request-step-up-open-body"}>
                        {openUntil(stepUp.verifiedUntil)}
                    </MessageBarBody>
                </MessageBar>
            )
            : null;
    }

    return (
        <section id={"information-request-step-up"}
                 aria-labelledby={"information-request-step-up-title"}
                 className={styles.panel}>
            <Text id={"information-request-step-up-title"}
                  weight={"semibold"}>
                Verify again to see protected questions
            </Text>
            <Text id={"information-request-step-up-copy"}
                  className={styles.mutedText}>
                {hiddenQuestions(awaiting)}
            </Text>
            {stepUp.codeSent && (
                <MessageBar id={"information-request-step-up-code-sent"}
                            intent={"success"}>
                    <MessageBarBody id={"information-request-step-up-code-sent-body"}>
                        A verification code has been sent.
                    </MessageBarBody>
                </MessageBar>
            )}
            {stepUp.error && (
                <MessageBar id={"information-request-step-up-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody id={"information-request-step-up-error-body"}>{stepUp.error}</MessageBarBody>
                </MessageBar>
            )}
            {accessLinkToken
                ? (
                    <StepUpCodeEntry code={stepUp.code}
                                     busy={stepUp.busy}
                                     onCodeChange={stepUp.setCode}
                                     onSendCode={() => void stepUp.sendCode()}
                                     onVerify={() => void stepUp.verifyCode()}/>
                )
                : (
                    <Button id={"information-request-step-up-account-btn"}
                            shape={"circular"}
                            appearance={"primary"}
                            className={styles.accountAction}
                            icon={accountIcon}
                            disabled={stepUp.busy}
                            onClick={() => { void stepUp.verifyAccount(); }}>
                        Verify again
                    </Button>
                )}
        </section>
    );
};

export default StepUpPanel;
