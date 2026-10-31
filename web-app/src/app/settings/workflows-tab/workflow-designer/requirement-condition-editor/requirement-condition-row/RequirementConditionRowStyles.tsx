import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequirementConditionRowStyles = makeStyles({
    row: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "flex-end",
        columnGap: tokens.spacingHorizontalS,
        rowGap: tokens.spacingVerticalXS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalS}`,
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
        minWidth: 0,
    },
    cell: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalXXS,
        minWidth: "140px",
        flexGrow: 1,
        flexBasis: "140px",
    },
    answer: {
        overflowWrap: "anywhere",
    },
    hint: {
        color: tokens.colorNeutralForeground3,
    },
});
