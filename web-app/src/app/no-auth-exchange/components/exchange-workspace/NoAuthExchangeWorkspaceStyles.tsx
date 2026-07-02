import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeWorkspaceStyles = makeStyles({
    container: {
        display: "grid",
        gridTemplateColumns: "minmax(280px, 0.72fr) minmax(0, 1.55fr)",
        alignItems: "start",
        gap: "24px",
        flex: 1,
        maxWidth: "1240px",
        width: "100%",
        margin: "0 auto",
        padding: "112px 32px 56px",
        boxSizing: "border-box",
        '@media (max-width: 920px)': {
            gridTemplateColumns: "1fr",
            maxWidth: "760px",
        },
        '@media (max-width: 640px)': {
            padding: "88px 16px 32px",
            gap: "16px",
        },
    },

    documentWorkspace: {
        display: "flex",
        flexDirection: "column",
        gap: "20px",
        minWidth: 0,
        background: tokens.colorNeutralBackground1,
        padding: "24px",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        boxShadow: tokens.shadow16,
        borderRadius: tokens.borderRadiusXLarge,
        '@media (max-width: 640px)': {
            padding: "18px 12px 12px",
            borderRadius: tokens.borderRadiusLarge,
            gap: "16px",
        },
    },

    workspaceHeading: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: "16px",
        paddingBottom: "18px",
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        '@media (max-width: 520px)': {
            flexDirection: "column",
        },
    },

    workspaceTitleGroup: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
    },

    workspaceDescription: {
        color: tokens.colorNeutralForeground3,
        lineHeight: "1.5",
    },

    privateLabel: {
        flexShrink: 0,
        padding: "6px 10px",
        color: tokens.colorBrandForeground1,
        background: tokens.colorBrandBackground2,
        borderRadius: "999px",
        fontSize: "12px",
        fontWeight: 600,
    },
});
