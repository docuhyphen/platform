import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestAmendmentSummaryStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        padding: tokens.spacingVerticalM,
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
        minWidth: 0,
    },
    amendment: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    header: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    changes: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalL,
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
});
