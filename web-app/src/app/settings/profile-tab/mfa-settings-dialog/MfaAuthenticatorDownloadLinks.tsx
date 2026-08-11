import {Link} from "@fluentui/react-components";
import {MfaMethod} from "../../../models/models.tsx";

type AuthenticatorProvider = Exclude<MfaMethod, 'EMAIL'>;
const appStoreBadgeUrl = "https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg";
const playStoreBadgeUrl = "https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png";

interface AuthenticatorDownloadLink
{
    appStore: string;
    playStore: string;
}

const downloadLinks: Record<AuthenticatorProvider, AuthenticatorDownloadLink> = {
    GOOGLE_AUTHENTICATOR: {
        appStore: "https://apps.apple.com/us/app/google-authenticator/id388497605",
        playStore: "https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2",
    },
    MICROSOFT_AUTHENTICATOR: {
        appStore: "https://apps.apple.com/us/app/microsoft-authenticator/id983156458",
        playStore: "https://play.google.com/store/apps/details?id=com.azure.authenticator",
    },
};

interface MfaAuthenticatorDownloadLinksProps
{
    appStoreBadgeClassName: string;
    linkGroupClassName: string;
    playStoreBadgeClassName: string;
    provider: AuthenticatorProvider;
}

const MfaAuthenticatorDownloadLinks = ({
    appStoreBadgeClassName,
    linkGroupClassName,
    playStoreBadgeClassName,
    provider,
}: MfaAuthenticatorDownloadLinksProps) =>
{
    const links = downloadLinks[provider];

    return (
        <div
            id={"mfa-authenticator-download-links"}
            className={linkGroupClassName}>
            <Link
                id={"mfa-authenticator-app-store-link"}
                href={links.appStore}
                target={"_blank"}
                rel={"noreferrer"}>
                <img
                    id={"mfa-authenticator-app-store-badge"}
                    className={appStoreBadgeClassName}
                    src={appStoreBadgeUrl}
                    alt={"Download on the App Store"}
                />
            </Link>
            <Link
                id={"mfa-authenticator-play-store-link"}
                href={links.playStore}
                target={"_blank"}
                rel={"noreferrer"}>
                <img
                    id={"mfa-authenticator-play-store-badge"}
                    className={playStoreBadgeClassName}
                    src={playStoreBadgeUrl}
                    alt={"Get it on Google Play"}
                />
            </Link>
        </div>
    );
};

export default MfaAuthenticatorDownloadLinks;
