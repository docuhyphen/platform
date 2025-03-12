import React, {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Link,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Text,
    Textarea
} from "@fluentui/react-components";
import {useNoAuthSessionDocumentListStyles} from "./NoAuthSessionUserDecisionStyles.tsx";
import {DismissRegular} from "@fluentui/react-icons";
import {NoAuthSharingSessionBasicDto, SharingSessionStatus} from "../../../models/models.tsx";
import {updateNoAuthSharingSession} from "../../../../services/sharingSessionApi.ts";

interface NoAuthSessionUserDecisionProps
{
    session: NoAuthSharingSessionBasicDto;
    onAccepted: (session: NoAuthSharingSessionBasicDto) => void;
    onDeclined: () => void;
}

const NoAuthSessionUserDecision: React.FC<NoAuthSessionUserDecisionProps> = (
    {
        session,
        onAccepted,
        onDeclined
    }) =>
{
    const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);
    const [isAcceptDialogOpen, setIsAcceptDialogOpen] = useState<boolean>(false);
    const [isDeclineDialogOpen, setIsDeclineDialogOpen] = useState<boolean>(false);
    const [isAcceptingSession, setIsAcceptingSession] = useState<boolean>(false);
    const [isDecliningSession, setIsDecliningSession] = useState<boolean>(false);
    const [acceptOTP, setAcceptOTP] = useState<string[]>(['', '', '', '', '']);
    const styles = useNoAuthSessionDocumentListStyles();

    const onAccept = async () =>
    {
        setIsAcceptDialogOpen(true);
    }

    const onDecline = async () =>
    {
        setIsDeclineDialogOpen(true);
    }

    const onCancelAccept = () =>
    {
        setErrorMessage(undefined);
        setAcceptOTP(['', '', '', '', '']);
        setIsAcceptDialogOpen(false);
    }

    const onCancelDecline = () =>
    {
        setErrorMessage(undefined);
        setIsDeclineDialogOpen(false);
    }

    const onContinueAccept = async () =>
    {
        if (isAcceptingSession)
        {
            return;
        }

        setIsAcceptingSession(true);
        setErrorMessage(undefined);

        try
        {
            const acceptRequest = {
                otp: acceptOTP.join(''),
                status: SharingSessionStatus.ACCEPTED_STARTED
            };

            const acceptedSession = await updateNoAuthSharingSession(session.id, acceptRequest);

            onAccepted(acceptedSession as NoAuthSharingSessionBasicDto);
            setIsAcceptDialogOpen(false);
        }
        catch (error)
        {
            alert('Failed to accept the request. Please try again later.');
            setErrorMessage('Failed to accept the request. Please try again later.');
        }
        finally
        {
            setIsAcceptingSession(false);
        }
    }

    const onContinueDecline = async () =>
    {
        if (isDecliningSession)
        {
            return;
        }

        setErrorMessage(undefined);
        setIsDecliningSession(true);

        try
        {
            const declineRequest = {
                otp: acceptOTP.join(''),
                rejectReason: '',
                status: SharingSessionStatus.REJECTED
            }

            await updateNoAuthSharingSession(session.id, declineRequest);
            onDeclined();
            setIsDeclineDialogOpen(false);
        }
        catch (error)
        {
            setErrorMessage('Failed to decline the request. Please try again later.');
        }
        finally
        {
            setIsDecliningSession(false);
        }
    }

    const renderErrorMessage = () => (
        errorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    {errorMessage}
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            onClick={() => setErrorMessage(undefined)}
                            appearance="transparent"
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    const handleOtpChange = (index: number, value: string) =>
    {
        const newOtp = [...acceptOTP];
        newOtp[index] = value;
        setAcceptOTP(newOtp);
    };

    return (
        <>
            <section className={styles.container}>
                <Text size={500}
                      align={"center"}>
                    {session.initiatorFirstName} {session.initiatorLastName} has requested to share documents with you.
                </Text>
                {session.initialShareMessage &&
                    <Text size={300} align={"center"}>
                        {session.initialShareMessage}
                    </Text>
                }
                <div className={styles.decisionActions}>
                    <Button onClick={onAccept}
                            shape={"circular"}
                            appearance={"primary"}
                            size={"large"}>
                        Accept
                    </Button>
                    <Button onClick={onDecline}
                            className={styles.declineButton}
                            shape={"circular"}
                            appearance={"subtle"}
                            size={"large"}>
                        Decline
                    </Button>
                </div>
                <div
                    className={styles.termsAndConditions}>
                    <Text size={300}
                          align={"center"}>
                        By accepting, you agree to the terms of the Doc-Hyphen app document sharing.
                    </Text>
                    <Text size={300}
                          align={"center"}>
                        Don't recognise the sender? report <Link>here</Link>
                    </Text>
                </div>
                <Dialog open={isDeclineDialogOpen} modalType={"alert"}>
                    <DialogSurface>
                        <DialogBody>
                            <DialogTitle>Declining</DialogTitle>
                            <DialogContent className={styles.declineDialogContent}>
                                <Field>
                                    <Textarea placeholder={"Reason for declining"}
                                              maxLength={100}
                                              minLength={10}/>
                                </Field>
                            </DialogContent>
                        </DialogBody>
                        <DialogActions>
                            <DialogTrigger>
                                <Button onClick={onContinueDecline}
                                        shape={"circular"}
                                        appearance={"primary"}>
                                    Decline
                                </Button>
                            </DialogTrigger>
                            <Button onClick={onCancelDecline}
                                    appearance={"subtle"}>
                                Cancel
                            </Button>
                        </DialogActions>
                    </DialogSurface>
                </Dialog>
                <Dialog open={isAcceptDialogOpen}>
                    <DialogSurface>
                        <DialogBody>
                            <DialogTitle>Accepting</DialogTitle>
                            <DialogContent className={styles.acceptDialogContent}>
                                <Text size={300}>
                                    Enter the OTP sent to your email address to accept the request. If you didn't
                                    receive the OTP,
                                    contact the sender to regenerate the OTP.
                                </Text>

                                {renderErrorMessage()}

                                <div className={styles.otpInputGroup}>
                                    {acceptOTP.map((otp, index) => (
                                        <Field key={index}>
                                            <Input className={styles.otpInput}
                                                   maxLength={1}
                                                   minLength={1}
                                                   value={otp}
                                                   onChange={(e) => handleOtpChange(index, e.target.value)}/>
                                        </Field>
                                    ))}
                                </div>
                            </DialogContent>
                        </DialogBody>
                        <DialogActions>
                            <DialogTrigger>
                                <Button onClick={onContinueAccept}
                                        shape={"circular"}
                                        appearance={"primary"}>
                                    Accept
                                </Button>
                            </DialogTrigger>
                            <Button onClick={onCancelAccept}
                                    shape={"circular"}
                                    appearance={"subtle"}>
                                Cancel
                            </Button>
                        </DialogActions>
                    </DialogSurface>
                </Dialog>
            </section>
        </>
    );
}

export default NoAuthSessionUserDecision;