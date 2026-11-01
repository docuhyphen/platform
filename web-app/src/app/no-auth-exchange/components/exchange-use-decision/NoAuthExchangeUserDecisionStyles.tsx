import {makeStyles, tokens} from "@fluentui/react-components";

export const useNoAuthExchangeUserDecisionStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        padding: `calc(${tokens.spacingHorizontalXXXL} + ${tokens.spacingHorizontalL})`,
        gap: tokens.spacingHorizontalL,
        maxWidth: "680px",
        width: "100%",
        margin: "0 auto",
        boxShadow: tokens.shadow4,
        borderRadius: tokens.borderRadiusMedium,
        background: tokens.colorNeutralBackground1,
    },

    decisionActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
    },

    termsAndConditions: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        marginTop: tokens.spacingVerticalXXXL,
    },

    declineButton: {
        '&:hover': {
            color: tokens.colorStatusDangerForeground1
        }
    },

    srOnly: {
        position: "absolute",
        width: "1px",
        height: "1px",
        padding: 0,
        margin: "-1px",
        overflow: "hidden",
        clip: "rect(0, 0, 0, 0)",
        border: 0,
    },
})
