import {makeStyles, tokens} from "@fluentui/react-components";
import {SETTINGS_HEADER_HEIGHT} from "../../SettingsStyles.tsx";

export const useOrganizationPendingReviewStyles = makeStyles({
    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        maxWidth: "30rem",
        minHeight: `calc(100vh - ${SETTINGS_HEADER_HEIGHT} - 8rem)`,
        gap: tokens.spacingHorizontalL,
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        margin: "0 auto",
        padding: tokens.spacingHorizontalL,
        boxSizing: "border-box",
    },
    onboardingRow: {
        marginTop: tokens.spacingVerticalS,
    },
});
