import React from "react";
import {Button, Link, MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import {useNoAuthExchangeUserDecisionStyles} from "./NoAuthExchangeUserDecisionStyles.tsx";
import {NoAuthExchangeBasicDto} from "../../../models/models.tsx";
import useNoAuthExchangeUserDecision from "./useNoAuthExchangeUserDecision.ts";
import {NoAuthExchangeDecisionOtpInputProps} from "./noAuthExchangeDecision.ts";
import NoAuthExchangeDeclineDialog from "./decline-dialog/NoAuthExchangeDeclineDialog.tsx";
import NoAuthExchangeAcceptDialog from "./accept-dialog/NoAuthExchangeAcceptDialog.tsx";

interface NoAuthExchangeUserDecisionProps
{
    exchange: NoAuthExchangeBasicDto;
    onAccepted: (exchange: NoAuthExchangeBasicDto) => void;
    onDeclined: () => void;
}

const NoAuthExchangeUserDecision: React.FC<NoAuthExchangeUserDecisionProps> = (
    {
        exchange,
        onAccepted,
        onDeclined
    }) =>
{
    const styles = useNoAuthExchangeUserDecisionStyles();
    const decision = useNoAuthExchangeUserDecision(exchange, onAccepted, onDeclined);

    const otpInput: NoAuthExchangeDecisionOtpInputProps = {
        recipientEmail: exchange.recipientEmail,
        decisionStage: decision.decisionStage,
        errorMessage: decision.errorMessage,
        onDismissError: decision.dismissError,
        decisionOtp: decision.decisionOtp,
        otpInputRefs: decision.otpInputRefs,
        onOtpChange: decision.handleOtpChange,
        onOtpKeyDown: decision.handleOtpKeyDown,
        onOtpPaste: decision.handleOtpPaste,
    };

    return (
        <>
            <section id={"no-auth-exchange-user-decision-section-1"}
                     className={styles.container}>
                <Text id={"no-auth-exchange-user-decision-text-3"}
                      size={500}
                      align={"center"}>
                    {exchange.initiatorFirstName} {exchange.initiatorLastName} wants to exchange documents with you.
                </Text>
                {exchange.initialShareMessage &&
                    <Text id={"no-auth-exchange-user-decision-text-4"}
                          size={300}
                          align={"center"}>
                        {exchange.initialShareMessage}
                    </Text>
                }
                <div id={"no-auth-exchange-user-decision-div-2"}
                     className={styles.decisionActions}>
                    <Button id={"no-auth-exchange-decision-accept-btn"}
                            onClick={decision.onAccept}
                            disabled={!decision.isExchangeActionable}
                            shape={"circular"}
                            appearance={"primary"}
                            size={"large"}>
                        Accept
                    </Button>
                    <Button id={"no-auth-exchange-decision-decline-btn"}
                            onClick={decision.onDecline}
                            disabled={!decision.isExchangeActionable}
                            className={styles.declineButton}
                            shape={"circular"}
                            appearance={"subtle"}
                            size={"large"}>
                        Decline
                    </Button>
                </div>
                {!decision.isExchangeActionable &&
                    <MessageBar id={"no-auth-exchange-user-decision-message-bar-2"}
                                intent={"warning"}>
                        <MessageBarBody id={"no-auth-exchange-user-decision-message-bar-body-2"}>
                            This sharing request is no longer awaiting a decision.
                        </MessageBarBody>
                    </MessageBar>
                }
                <div
                    id={"no-auth-exchange-user-decision-div-3"}
                    className={styles.termsAndConditions}>
                    <Text id={"no-auth-exchange-user-decision-text-5"}
                          size={300}
                          align={"center"}>
                        By accepting, you agree to the terms of the Doc-Hyphen app document sharing.
                    </Text>
                    <Text id={"no-auth-exchange-user-decision-text-6"}
                          size={300}
                          align={"center"}>
                        Don't recognise the sender? report <Link id={"no-auth-exchange-user-decision-link-1"}>here</Link>
                    </Text>
                </div>
                <NoAuthExchangeDeclineDialog open={decision.isDeclineDialogOpen}
                                             declineReason={decision.declineReason}
                                             onDeclineReasonChange={decision.setDeclineReason}
                                             isDeclining={decision.isDecliningExchange}
                                             canConfirm={decision.isDecisionOtpComplete && decision.isDeclineReasonValid}
                                             onConfirm={decision.onContinueDecline}
                                             onCancel={decision.onCancelDecline}
                                             otpInput={otpInput}/>
                <NoAuthExchangeAcceptDialog open={decision.isAcceptDialogOpen}
                                            isAccepting={decision.isAcceptingExchange}
                                            canConfirm={decision.isDecisionOtpComplete}
                                            onConfirm={decision.onContinueAccept}
                                            onCancel={decision.onCancelAccept}
                                            otpInput={otpInput}/>
                <div id={"no-auth-exchange-user-decision-div-4"}
                     aria-live={"polite"}
                     className={styles.srOnly}>{decision.otpRequestNotice || decision.errorMessage || ''}</div>
            </section>
        </>
    );
}

export default NoAuthExchangeUserDecision;
