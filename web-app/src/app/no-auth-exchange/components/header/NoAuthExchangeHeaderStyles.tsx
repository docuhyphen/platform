import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeHeaderStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "12px 24px",
        borderBottom: `1px solid ${tokens.colorNeutralStroke1}`,
        boxShadow: tokens.shadow8,
        background: tokens.colorNeutralBackground1,
        position: "fixed",
        zIndex: 10,
        width: "100%",
        boxSizing: "border-box",
        '@media (max-width: 640px)': {
            padding: "10px 14px",
        },
    },

    brandContainer: {
        display: "flex",
        alignItems: "center",
        gap: "14px",
    },

    secureRequestLabel: {
        paddingLeft: "14px",
        color: tokens.colorNeutralForeground3,
        borderLeft: `1px solid ${tokens.colorNeutralStroke1}`,
        fontSize: "12px",
        fontWeight: 600,
        '@media (max-width: 520px)': {
            display: "none",
        },
    },

    signInButtonContainer: {
        display: "flex",
        flexDirection: "row",
        gap: "10px",
        alignItems: "center",
    },

    signInPrompt: {
        color: tokens.colorNeutralForeground3,
        fontSize: "13px",
        '@media (max-width: 520px)': {
            display: "none",
        },
    },

    signInButton: {
        display: "flex",
        flexDirection: "row",
        gap: "4px",
        backgroundColor: tokens.colorBrandBackground,
    },
});
