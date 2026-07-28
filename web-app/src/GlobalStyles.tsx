import {makeStyles, tokens} from "@fluentui/react-components";

export const useGlobalStyles = makeStyles({
    root: {
        width: "100%",
        height: "100%",
        margin: 0,
        padding: 0,
    },
    fluentProvider: {
        width: "100%",
        height: "100%",
    },
    mainAppHeader: {
        display: "flex",
        width: "100%",
        justifyContent: "space-between",
        alignItems: "center",
        padding: `0 ${tokens.spacingHorizontalL}`,
        boxSizing: "border-box",
        boxShadow: tokens.shadow4,
        position: "fixed",
        zIndex: 2,
        background: tokens.colorNeutralBackground1,
        height: "60px",
        top: 0,
        gap: tokens.spacingHorizontalS,
        // Smaller screens: tighten the header and let the action cluster
        // sit closer together so the brand logo and the persona avatar
        // still both fit on a phone.
        "@media (max-width: 768px)": {
            padding: `0 ${tokens.spacingHorizontalS}`,
            gap: tokens.spacingHorizontalXS,
        },
        "@media (max-width: 480px)": {
            padding: `0 ${tokens.spacingHorizontalSNudge}`,
            gap: tokens.spacingHorizontalXXS,
        },
    },

    mainHeaderAppLogo: {
        flex: 1,
        minWidth: 0,
        overflow: "hidden",
        display: "flex",
        gap: tokens.spacingHorizontalS,
        alignItems: "center",
    },
    mainHeaderOrgTitle: {
        opacity: 0.3
    },

    mainAppSection: {
        width: "100%",
        height: "100%",
        paddingTop: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXL} + ${tokens.spacingVerticalXS})`,
        boxSizing: "border-box",
        background: tokens.colorNeutralBackground2,
    },
    mainAppSectionFullHeight: {
        width: "100%",
        height: "100%",
    },

    shadingSessionDocumentCard: {
        marginBottom: tokens.spacingVerticalM,
    },
    shadingSessionDocumentCardHeaderField: {
        flex: 1,
    },
    shadingSessionDocumentCardHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        boxSizing: "border-box",
        gap: tokens.spacingHorizontalS,
    },
    shadingSessionDocumentCardDocType: {
        display: "flex",
        justifyContent: "space-between",
    },
    preLoadingContainer: {
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "100%",
    },
    buttonWithLoading: {
        display: "flex",
        gap: tokens.spacingHorizontalSNudge,
    },
    flex1: {
        flex: 1,
    },
    textCenter: {
        textAlign: "center",
    },
});
