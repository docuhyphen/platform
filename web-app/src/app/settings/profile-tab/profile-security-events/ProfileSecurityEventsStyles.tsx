import {makeStyles, tokens} from "@fluentui/react-components";

export const useProfileSecurityEventsStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        height: "100%",
        minHeight: 0,
        overflow: "hidden",
    },
    header: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        flexShrink: 0,
        marginTop: tokens.spacingVerticalS,
        marginBottom: tokens.spacingVerticalS,
    },
    description: {
        color: tokens.colorNeutralForeground3,
    },
});
