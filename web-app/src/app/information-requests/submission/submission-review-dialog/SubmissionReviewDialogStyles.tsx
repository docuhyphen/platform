import {makeStyles, tokens} from "@fluentui/react-components";

export const useSubmissionReviewDialogStyles = makeStyles({
    list: {
        listStyleType: "none",
        marginTop: 0,
        marginBottom: 0,
        paddingLeft: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    item: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        paddingBottom: tokens.spacingVerticalS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    answer: {
        color: tokens.colorNeutralForeground2,
        overflowWrap: "anywhere",
    },
});
