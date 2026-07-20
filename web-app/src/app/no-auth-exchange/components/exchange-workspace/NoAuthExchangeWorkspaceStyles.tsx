import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeWorkspaceStyles = makeStyles({
    container: {
        display: "grid",
        gridTemplateColumns: "minmax(280px, 0.72fr) minmax(0, 1.55fr)",
        alignItems: "start",
        gap: tokens.spacingHorizontalXXL,
        flex: 1,
        maxWidth: "1240px",
        width: "100%",
        margin: "0 auto",
        padding: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL}) ${tokens.spacingHorizontalXXXL} calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXL})`,
        boxSizing: "border-box",
        '@media (max-width: 920px)': {
            gridTemplateColumns: "1fr",
            maxWidth: "760px",
            display: "flex",
            flexDirection: "column"
        },
        '@media (max-width: 640px)': {
            padding: `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalXXL}) ${tokens.spacingHorizontalL} ${tokens.spacingVerticalXXXL}`,
            gap: tokens.spacingHorizontalL,
            display: "flex"
        },
    },

    documentWorkspace: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        minWidth: 0,
        background: tokens.colorNeutralBackground1,
        padding: tokens.spacingHorizontalXXL,
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        boxShadow: tokens.shadow16,
        borderRadius: tokens.borderRadiusXLarge,
        '@media (max-width: 640px)': {
            padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalM} ${tokens.spacingVerticalM}`,
            borderRadius: tokens.borderRadiusLarge,
            gap: tokens.spacingHorizontalL,
        },
    },

    workspaceHeading: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalL,
        paddingBottom: tokens.spacingVerticalL,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        '@media (max-width: 520px)': {
            flexDirection: "column",
        },
    },

    workspaceTitleGroup: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
    },

    workspaceDescription: {
        color: tokens.colorNeutralForeground3,
        lineHeight: "1.5",
    },

    privateLabel: {
        flexShrink: 0,
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalMNudge}`,
        color: tokens.colorBrandForeground1,
        background: tokens.colorBrandBackground2,
        borderRadius: "999px",
        fontSize: "12px",
        fontWeight: 600,
    },
});
