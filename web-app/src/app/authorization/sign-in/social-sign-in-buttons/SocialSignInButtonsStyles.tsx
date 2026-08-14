import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useSocialSignInButtonsStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        width: "100%",
    },
    providerButton: {
        width: "100%",
        minHeight: "40px",
        justifyContent: "center",
        fontWeight: tokens.fontWeightSemibold,
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke1),
        backgroundColor: tokens.colorNeutralBackground1,
        color: tokens.colorNeutralForeground1,
        ":hover": {
            backgroundColor: tokens.colorNeutralBackground1Hover,
            color: tokens.colorNeutralForeground1,
        },
    },
    providerIcon: {
        width: "18px",
        height: "18px",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
    },
});
