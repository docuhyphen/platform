import {
    Button,
    Persona,
    SplitButton,
} from "@fluentui/react-components";
import {
    AlertFilled,
    AlertRegular,
    bundleIcon,
    ChannelShareFilled,
    ChannelShareRegular,
    ShareAndroidRegular,
} from "@fluentui/react-icons";
import logo from "../../../../assets/logo.svg";
import type {IndustryPersona} from "../featureContent.ts";
import {useIndustryDemoHeaderStyles} from "./IndustryDemoHeaderStyles.tsx";

interface IndustryDemoHeaderProps
{
    persona: IndustryPersona;
}

const ExchangeIcon = bundleIcon(ChannelShareFilled, ChannelShareRegular);
const NotificationsIcon = bundleIcon(AlertFilled, AlertRegular);

export function IndustryDemoHeader({persona}: IndustryDemoHeaderProps)
{
    const styles = useIndustryDemoHeaderStyles();

    return (
        <header
            id="industry-demo-app-header"
            className={styles.header}
        >
            <div
                id="industry-demo-brand"
                className={styles.brand}
            >
                <img
                    id="industry-demo-logo"
                    className={styles.logo}
                    src={logo}
                    alt="DocuHyphen"
                />
                <span
                    id="industry-demo-organization-short-name"
                    className={styles.organizationShortName}
                >
                    {persona.organizationShortName}
                </span>
            </div>

            <div
                id="industry-demo-header-actions"
                className={styles.actions}
            >
                <SplitButton
                    id="industry-demo-start-exchange"
                    appearance="primary"
                    className={styles.startButton}
                    shape="circular"
                    menuButton={{"aria-label": "Choose sharing type"}}
                    icon={
                        <ShareAndroidRegular
                            id="industry-demo-start-exchange-icon"
                            aria-hidden="true"
                        />
                    }
                >
                    <span
                        id="industry-demo-start-exchange-label"
                    >
                        Start Exchange
                    </span>
                </SplitButton>
                <Button
                    id="industry-demo-exchanges-button"
                    appearance="subtle"
                    className={styles.iconButton}
                    shape="circular"
                    aria-label="Open Exchanges"
                    icon={
                        <ExchangeIcon
                            id="industry-demo-exchanges-icon"
                            aria-hidden="true"
                        />
                    }
                />
                <div
                    id="industry-demo-notification-control"
                    className={styles.notificationControl}
                >
                    <Button
                        id="industry-demo-notifications-button"
                        appearance="subtle"
                        className={styles.iconButton}
                        shape="circular"
                        aria-label="Open notifications"
                        icon={
                            <NotificationsIcon
                                id="industry-demo-notifications-icon"
                                aria-hidden="true"
                            />
                        }
                    />
                    <span
                        id="industry-demo-notification-indicator"
                        className={styles.notificationIndicator}
                        aria-label="Unread notifications"
                    />
                </div>
                <Button
                    id="industry-demo-persona"
                    className={styles.persona}
                    appearance="subtle"
                    shape="circular"
                    aria-label={`Account menu for ${persona.email}`}
                >
                    <Persona
                        id="industry-demo-persona-details"
                        name={persona.name}
                        secondaryText={persona.email}
                    />
                </Button>
            </div>
        </header>
    );
}
