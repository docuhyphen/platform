import {makeStyles, tokens} from "@fluentui/react-components";

export const useSubmissionPackageListStyles = makeStyles({
    list: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        minWidth: 0,
    },
    row: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
        paddingTop: tokens.spacingVerticalXS,
        paddingBottom: tokens.spacingVerticalXS,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    summary: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        flexGrow: 1,
        minWidth: 0,
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
});
