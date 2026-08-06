import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuthorizationStyles = makeStyles({
    auth: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "100%",
        minHeight: "100vh",
        padding: tokens.spacingHorizontalXXL,
        boxSizing: "border-box",
        background: tokens.colorNeutralBackground2,

        "@media (max-width: 768px)": {
            padding: `${tokens.spacingVerticalXL} ${tokens.spacingHorizontalS}`,
            alignItems: "stretch",
        },
    },
    authSection: {
        display: "flex",
        alignItems: "stretch",
        justifyContent: "center",
        height: "auto",
        flexDirection: "row",
        maxWidth: "100%",
        width: "800px",
        minHeight: "650px",
        borderRadius: tokens.borderRadiusXLarge,
        boxShadow: tokens.shadow16,
        background: tokens.colorNeutralBackground1,

        "@media (max-width: 768px)": {
            width: "100%",
            minHeight: "unset",
            flex: 1,
        },
    },
    authSection1: {
        borderRadius: `${tokens.borderRadiusXLarge} 0 0 ${tokens.borderRadiusXLarge}`,
        display: "flex",
        flexDirection: "column",
        height: "auto",
        boxSizing: "border-box",
        flex: 1,
        padding: tokens.spacingHorizontalXXXL,
        maxWidth: "50%",

        "@media (max-width: 768px)": {
            maxWidth: "100%",
            padding: tokens.spacingHorizontalXL,
        },
    },
    authSection2: {
        borderRadius: `0 ${tokens.borderRadiusXLarge} ${tokens.borderRadiusXLarge} 0`,
        background: tokens.colorBrandBackground,
        color: tokens.colorNeutralForegroundOnBrand,
        height: "auto",
        boxSizing: "border-box",
        flex: 1,
        padding: tokens.spacingHorizontalXXXL,
        maxWidth: "50%",

        "@media (max-width: 768px)": {
            display: "none",
        },
    },
    commonAuthSection: {
        height: "100%",
        boxSizing: "border-box",
        flex: 1,
        padding: tokens.spacingHorizontalXXXL,
        maxWidth: "50%",
    },
    authorizationFormSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        flex: "1",
        justifyContent: "center",
        overflowY: "auto",
        minHeight: 0,
    },
});