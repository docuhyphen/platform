import {makeStyles, tokens} from "@fluentui/react-components";

export const useRespondentItemThreadStyles = makeStyles({
    thread: {
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
    form: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    actions: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
    },
});
