import {Button} from "@fluentui/react-components";
import {getApiBaseUrl} from "../../../../services/apiBaseUrl.ts";
import {useSocialSignInButtonsStyles} from "./SocialSignInButtonsStyles.tsx";

const startOAuthSignIn = (provider: "GOOGLE" | "MICROSOFT") =>
{
    const apiBaseUrl = getApiBaseUrl();
    window.location.href = `${apiBaseUrl}/auth/oauth/${provider}/authorize?flow=signin`;
};

const GoogleBrandMark = () => (
    <svg
        id={"sign-in-google-brand-mark"}
        viewBox={"0 0 18 18"}
        aria-hidden={"true"}>
        <path
            fill={"#4285F4"}
            d={"M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62z"}
        />
        <path
            fill={"#34A853"}
            d={"M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.33-1.58-5.04-3.7H.94v2.34A9 9 0 0 0 9 18z"}
        />
        <path
            fill={"#FBBC05"}
            d={"M3.96 10.72A5.41 5.41 0 0 1 3.68 9c0-.6.1-1.18.28-1.72V4.94H.94A9 9 0 0 0 0 9c0 1.45.34 2.82.94 4.06l3.02-2.34z"}
        />
        <path
            fill={"#EA4335"}
            d={"M9 3.58c1.32 0 2.5.46 3.44 1.34l2.58-2.58C13.46.9 11.43 0 9 0A9 9 0 0 0 .94 4.94l3.02 2.34C4.67 5.16 6.66 3.58 9 3.58z"}
        />
    </svg>
);

const MicrosoftBrandMark = () => (
    <svg
        id={"sign-in-microsoft-brand-mark"}
        viewBox={"0 0 18 18"}
        aria-hidden={"true"}>
        <rect
            fill={"#F25022"}
            height={"8"}
            width={"8"}
        />
        <rect
            fill={"#7FBA00"}
            height={"8"}
            width={"8"}
            x={"10"}
        />
        <rect
            fill={"#00A4EF"}
            height={"8"}
            width={"8"}
            y={"10"}
        />
        <rect
            fill={"#FFB900"}
            height={"8"}
            width={"8"}
            x={"10"}
            y={"10"}
        />
    </svg>
);

const SocialSignInButtons = () =>
{
    const styles = useSocialSignInButtonsStyles();

    return (
        <div
            id={"social-sign-in-buttons"}
            className={styles.container}>
            <Button
                id={"sign-in-microsoft-btn"}
                className={styles.providerButton}
                icon={
                    <span
                        id={"sign-in-microsoft-icon"}
                        className={styles.providerIcon}>
                        <MicrosoftBrandMark/>
                    </span>
                }
                onClick={() => startOAuthSignIn("MICROSOFT")}
                appearance={"secondary"}
                shape={"circular"}>
                Sign in with Microsoft
            </Button>
            <Button
                id={"sign-in-google-btn"}
                className={styles.providerButton}
                icon={
                    <span
                        id={"sign-in-google-icon"}
                        className={styles.providerIcon}>
                        <GoogleBrandMark/>
                    </span>
                }
                onClick={() => startOAuthSignIn("GOOGLE")}
                appearance={"secondary"}
                shape={"circular"}>
                Sign in with Google
            </Button>
        </div>
    );
};

export default SocialSignInButtons;
