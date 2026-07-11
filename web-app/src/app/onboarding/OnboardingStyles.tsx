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
        margin: `calc(${tokens.spacingHorizontalXXXL} + ${tokens.spacingHorizontalXXXL} + ${tokens.spacingHorizontalL})`,
        maxHeight: "650px",
        borderRadius: tokens.borderRadiusXLarge,
        boxShadow: tokens.shadow16,
        background: tokens.colorNeutralBackground1,
    },
    onboardingSection1: {
        borderRadius: `${tokens.borderRadiusXLarge} 0 0 ${tokens.borderRadiusXLarge}`,
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        height: "100%",
        boxSizing: "border-box",
        flex: 1,
        padding: tokens.spacingHorizontalXXXL,
        maxWidth: "50%",
    },
    onboardingSection2: {
        borderRadius: `0 ${tokens.borderRadiusXLarge} ${tokens.borderRadiusXLarge} 0`,
        background: tokens.colorBrandBackground,
        color: tokens.colorNeutralForegroundOnBrand,
        height: "100%",
        boxSizing: "border-box",
        flex: 1,
        padding: tokens.spacingHorizontalXXXL,
        maxWidth: "50%",
        alignItems: "center",
        display: "flex",
        justifyContent: "center",
        flexDirection: "column",
    },

    onboardingSection2_1: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },

    orgOnboardingContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        justifyContent: "space-between",
    }
});