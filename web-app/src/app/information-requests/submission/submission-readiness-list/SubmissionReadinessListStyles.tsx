import {makeStyles, tokens} from "@fluentui/react-components";

export const useSubmissionReadinessListStyles = makeStyles({
    ready: {
        color: tokens.colorPaletteGreenForeground1,
    },
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        minWidth: 0,
    },
    problems: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    problem: {
        display: "flex",
        flexWrap: "wrap",
        columnGap: tokens.spacingHorizontalS,
        rowGap: tokens.spacingVerticalXXS,
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
});
