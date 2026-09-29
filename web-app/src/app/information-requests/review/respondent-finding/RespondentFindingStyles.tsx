import {makeStyles, tokens} from "@fluentui/react-components";

export const useRespondentFindingStyles = makeStyles({
    finding: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
    },
    conversation: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
        listStyleType: "none",
    },
    comment: {
        color: tokens.colorNeutralForeground2,
    },
    reply: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    replyActions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
    },
});
