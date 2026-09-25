import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestCarryForwardListStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalM,
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
        minWidth: 0,
    },
    row: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    detail: {
        color: tokens.colorNeutralForeground3,
        overflowWrap: "anywhere",
    },
});
