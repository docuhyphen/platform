import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeAccessVerificationPanelStyles = makeStyles({
    verificationPanel: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        background: tokens.colorNeutralBackground1,
        padding: tokens.spacingHorizontalM,
        borderRadius: tokens.borderRadiusLarge,
        border: `1px solid ${tokens.colorNeutralStroke1}`,
    },

    verificationControls: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        alignItems: "flex-end",
        flexWrap: "wrap",
    },

    otpInputField: {
        minWidth: "220px",
        flex: "1 1 220px",
    },

    panelMessage: {
        whiteSpace: "pre-wrap",
        overflowWrap: "anywhere",
        wordBreak: "break-word",
        maxWidth: "100%",
        minWidth: 0,
    },

    verifyButton: {
        '@media (max-width: 640px)': {
            width: "100%",
            justifyContent: "center",
        },
    },
});

