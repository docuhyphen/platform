import {Button, Divider, Field, Input, Spinner, Switch, Text,} from "@fluentui/react-components";
import {Dispatch, SetStateAction} from "react";
import {MailEditIcon, RegenerateOTPIcon} from "../../../../components/IconBundles.tsx";
import ExchangeSettingsStatusMessages from "./ExchangeSettingsStatusMessages.tsx";
import {useExchangeSettingsPanelStyles} from "./ExchangeSettingsPanelStyles.tsx";
import RecipientSignInRequestNotice from "./RecipientSignInRequestNotice.tsx";

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
            <Divider id={"exchange-settings-panel-root-divider"}
                     alignContent={"start"}>Exchange options</Divider>
            <div id={"exchange-settings-panel-require-sign-in"}
                 className={styles.requireSignIn}>
                <Field id={"exchange-settings-panel-require-sign-in-field"}>
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
                        {props.sendingAccessCode && <Spinner id={"exchange-settings-panel-action-group-spinner"}
                                                             size={"tiny"}/>}
                        {props.resendCooldownRemaining > 0
                            ? `Send access code (${props.resendCooldownRemaining}s)`
                            : "Send access code"}
                    </Button>
                </div>
            </div>
            <Text id={"exchange-settings-panel-access-mode-label-text"}
                  size={200}>{accessModeLabel}</Text>
            {props.requireRecipientSignIn && <RecipientSignInRequestNotice/>}
            {!props.requireRecipientSignIn && (
                <Field id={"exchange-settings-panel-no-auth-access-expires-field"}
                       label={"No-auth access expires after (days)"}>
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
            <ExchangeSettingsStatusMessages accessCodeStatus={props.accessCodeStatus}
                                            invitationStatus={props.invitationStatus}
                                            accessCodeError={props.accessCodeError}
                                            invitationError={props.invitationError}/>
        </section>
    );
};

export default ExchangeSettingsPanel;
