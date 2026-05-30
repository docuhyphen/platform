import {makeStyles, tokens} from "@fluentui/react-components";

export const useOnboardingStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        height: "100%",
        background: tokens.colorNeutralBackground2,
    },

    onboardingSection: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
        flexDirection: "row",
        maxWidth: "100%",
        width: "800px",
        margin: "80px",
        maxHeight: "650px",
        borderRadius: "16px",
        boxShadow: tokens.shadow16,
        background: tokens.colorNeutralBackground1,
    },
    onboardingSection1: {
        borderRadius: "16px 0 0 16px",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        height: "100%",
        boxSizing: "border-box",
        flex: 1,
        padding: "38px",
        maxWidth: "50%",
    },
    onboardingSection2: {
        borderRadius: "0 16px 16px 0",
        background: tokens.colorBrandBackground,
        color: tokens.colorNeutralForegroundOnBrand,
        height: "100%",
        boxSizing: "border-box",
        flex: 1,
        padding: "38px",
        maxWidth: "50%",
        alignItems: "center",
        display: "flex",
        justifyContent: "center",
        flexDirection: "column",
    },

    onboardingSection2_1: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
    },

    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        justifyContent: "space-between",
    }
});