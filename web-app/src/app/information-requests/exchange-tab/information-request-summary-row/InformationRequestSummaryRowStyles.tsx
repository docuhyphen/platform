import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestSummaryRowStyles = makeStyles({
    row: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) auto",
        alignItems: "center",
        columnGap: tokens.spacingHorizontalL,
        rowGap: tokens.spacingVerticalS,
        paddingTop: tokens.spacingVerticalM,
        paddingBottom: tokens.spacingVerticalM,
        paddingLeft: tokens.spacingHorizontalM,
        paddingRight: tokens.spacingHorizontalM,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        "@media (max-width: 640px)": {
            gridTemplateColumns: "minmax(0, 1fr)",
        },
    },
    summary: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        minWidth: 0,
    },
    heading: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
    },
    title: {
        overflowWrap: "anywhere",
    },
    meta: {
        display: "flex",
        flexWrap: "wrap",
        columnGap: tokens.spacingHorizontalL,
        rowGap: tokens.spacingVerticalXXS,
        color: tokens.colorNeutralForeground2,
    },
    progress: {
        maxWidth: "320px",
    },
    action: {
        justifySelf: "end",
        "@media (max-width: 640px)": {
            justifySelf: "start",
        },
    },
});
