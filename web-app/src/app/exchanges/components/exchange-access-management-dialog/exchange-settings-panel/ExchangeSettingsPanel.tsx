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
import {handleCheckboxChange} from "../../../../exchange-initiation/formHandlers.tsx";
import {RegenerateOTPIcon} from "../../../../components/IconBundles.tsx";
import {useExchangeSettingsPanelStyles} from "./ExchangeSettingsPanelStyles.tsx";

interface ExchangeSettingsPanelProps
{
    requireRecipientSignIn: boolean;
    setRequireRecipientSignIn: Dispatch<SetStateAction<boolean>>;
    sendingAccessCode: boolean;
    resendCooldownRemaining: number;
    onSendAccessCode: () => void;
    buttonWithLoadingClassName: string;
    noAuthAccessValidityDays: string;
    setNoAuthAccessValidityDays: Dispatch<SetStateAction<string>>;
    accessCodeStatus: string;
    accessCodeError: string;
}

const ExchangeSettingsPanel = (props: ExchangeSettingsPanelProps) =>
{
    const styles = useExchangeSettingsPanelStyles();
    const accessModeLabel = props.requireRecipientSignIn
        ? "Recipient must sign in with account"
        : "Recipient can use one-time email access code";
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
                        onChange={handleCheckboxChange(props.setRequireRecipientSignIn)}
                    />
                </Field>
                <Button
                    id={"access-mgmt-send-access-code-btn"}
                    icon={<RegenerateOTPIcon/>}
                    className={props.buttonWithLoadingClassName}
                    appearance={"transparent"}
                    shape={"circular"}
                    disabled={props.requireRecipientSignIn || props.sendingAccessCode || props.resendCooldownRemaining > 0}
                    onClick={props.onSendAccessCode}
                >
                    {props.sendingAccessCode && <Spinner size={"tiny"}/>}
                    {props.resendCooldownRemaining > 0
                        ? `Resend access code (${props.resendCooldownRemaining}s)`
                        : "Send access code"}
                </Button>
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
            {props.accessCodeError && (
                <MessageBar intent={"error"}>
                    <MessageBarBody>{props.accessCodeError}</MessageBarBody>
                </MessageBar>
            )}
        </section>
    );
};

export default ExchangeSettingsPanel;
