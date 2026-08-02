import {
    Button,
    Divider,
    Field,
    Input,
    MessageBar,
    MessageBarBody,
    Spinner,
    Switch,
    Text,
} from "@fluentui/react-components";
import {Dispatch, SetStateAction} from "react";
import {MailEditIcon, RegenerateOTPIcon} from "../../../../components/IconBundles.tsx";
import {useExchangeSettingsPanelStyles} from "./ExchangeSettingsPanelStyles.tsx";

interface ExchangeSettingsPanelProps
{
    requireRecipientSignIn: boolean;
    setRequireRecipientSignIn: Dispatch<SetStateAction<boolean>>;
    sendingAccessCode: boolean;
    sendingInvitation: boolean;
    resendCooldownRemaining: number;
    onSendAccessCode: () => void;
    onResendInvitation: () => void;
    buttonWithLoadingClassName: string;
    noAuthAccessValidityDays: string;
    setNoAuthAccessValidityDays: Dispatch<SetStateAction<string>>;
    accessCodeStatus: string;
    accessCodeError: string;
    invitationStatus: string;
    invitationError: string;
}

const ExchangeSettingsPanel = (props: ExchangeSettingsPanelProps) =>
{
    const styles = useExchangeSettingsPanelStyles();
    const accessModeLabel = props.requireRecipientSignIn
        ? "Recipient must sign in with an account."
        : "Recipient can use one-time email access code.";
    return (
        <section
            id={"access-mgmt-exchange-settings"}
            className={styles.root}
        >
            <Divider alignContent={"start"}>Exchange options</Divider>
            <div className={styles.requireSignIn}>
                <Field>
                    <Switch
                        id={"switch-require-recipient-sign-in"}
                        label={"Require recipient sign in"}
                        checked={props.requireRecipientSignIn}
                        onChange={(_, data) => props.setRequireRecipientSignIn(data.checked)}
                    />
                </Field>
                <div
                    id={"access-mgmt-no-auth-action-row"}
                    className={styles.actionGroup}
                >
                    <Button
                        id={"access-mgmt-resend-invitation-btn"}
                        icon={<MailEditIcon/>}
                        className={props.buttonWithLoadingClassName}
                        appearance={"transparent"}
                        shape={"circular"}
                        disabled={props.requireRecipientSignIn
                            || props.sendingInvitation
                            || props.resendCooldownRemaining > 0}
                        onClick={props.onResendInvitation}
                    >
                        {props.sendingInvitation && (
                            <Spinner
                                id={"access-mgmt-resend-invitation-spinner"}
                                size={"tiny"}
                            />
                        )}
                        {props.resendCooldownRemaining > 0
                            ? `Resend invitation (${props.resendCooldownRemaining}s)`
                            : "Resend invitation"}
                    </Button>
                    <Button
                        id={"access-mgmt-send-access-code-btn"}
                        icon={<RegenerateOTPIcon/>}
                        className={props.buttonWithLoadingClassName}
                        appearance={"transparent"}
                        shape={"circular"}
                        disabled={props.requireRecipientSignIn
                            || props.sendingAccessCode
                            || props.resendCooldownRemaining > 0}
                        onClick={props.onSendAccessCode}
                    >
                        {props.sendingAccessCode && <Spinner size={"tiny"}/>}
                        {props.resendCooldownRemaining > 0
                            ? `Send access code (${props.resendCooldownRemaining}s)`
                            : "Send access code"}
                    </Button>
                </div>
            </div>
            <Text size={200}>{accessModeLabel}</Text>
            {!props.requireRecipientSignIn && (
                <Field label={"No-auth access expires after (days)"}>
                    <Input
                        id={"input-noauth-access-validity-days"}
                        type={"number"}
                        min={1}
                        max={30}
                        value={props.noAuthAccessValidityDays}
                        onChange={(_, data) => props.setNoAuthAccessValidityDays(data.value)}
                        contentAfter={"days"}
                    />
                </Field>
            )}
            {props.accessCodeStatus && (
                <MessageBar intent={"success"}>
                    <MessageBarBody>{props.accessCodeStatus}</MessageBarBody>
                </MessageBar>
            )}
            {props.invitationStatus && (
                <MessageBar
                    id={"access-mgmt-invitation-status"}
                    intent={"success"}
                >
                    <MessageBarBody id={"access-mgmt-invitation-status-body"}>
                        {props.invitationStatus}
                    </MessageBarBody>
                </MessageBar>
            )}
            {props.accessCodeError && (
                <MessageBar intent={"error"}>
                    <MessageBarBody>{props.accessCodeError}</MessageBarBody>
                </MessageBar>
            )}
            {props.invitationError && (
                <MessageBar
                    id={"access-mgmt-invitation-error"}
                    intent={"error"}
                >
                    <MessageBarBody id={"access-mgmt-invitation-error-body"}>
                        {props.invitationError}
                    </MessageBarBody>
                </MessageBar>
            )}
        </section>
    );
};

export default ExchangeSettingsPanel;
