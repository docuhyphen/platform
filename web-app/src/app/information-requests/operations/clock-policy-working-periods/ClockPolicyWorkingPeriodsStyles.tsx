import {makeStyles, tokens} from "@fluentui/react-components";

export const useClockPolicyWorkingPeriodsStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        minWidth: 0,
    },
    hint: {
        color: tokens.colorNeutralForeground2,
    },
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        listStyleType: "none",
        margin: 0,
        padding: 0,
    },
    problem: {
        color: tokens.colorPaletteRedForeground1,
    },
    add: {
        alignSelf: "flex-start",
    },
});
