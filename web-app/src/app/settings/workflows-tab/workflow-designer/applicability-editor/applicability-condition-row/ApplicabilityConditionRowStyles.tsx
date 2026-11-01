import {makeStyles, tokens} from "@fluentui/react-components";

export const useApplicabilityConditionRowStyles = makeStyles({
    row: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "flex-end",
        columnGap: tokens.spacingHorizontalS,
        rowGap: tokens.spacingVerticalXS,
        padding: tokens.spacingVerticalS,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    rowField: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalXXS,
        minWidth: "140px",
        flexGrow: 1,
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
});
