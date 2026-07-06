import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useBrowserNotificationsCardStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalL,
        ...shorthands.padding(tokens.spacingVerticalM, 0),
        ...shorthands.borderBottom("1px", "solid", tokens.colorNeutralStroke2),
        "@media (max-width: 640px)": {
            alignItems: "stretch",
            flexDirection: "column",
            gap: tokens.spacingVerticalM,
        },
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },
    description: {
        color: tokens.colorNeutralForeground3,
        maxWidth: "620px",
    },
    action: {
        flexShrink: 0,
        "@media (max-width: 640px)": {
            width: "100%",
        },
    },
});
