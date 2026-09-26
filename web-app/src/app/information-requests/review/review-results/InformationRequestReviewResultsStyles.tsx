import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestReviewResultsStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalM,
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
        minWidth: 0,
    },
});
