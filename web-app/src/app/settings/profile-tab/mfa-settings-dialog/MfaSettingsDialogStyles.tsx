import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useMfaSettingsDialogStyles = makeStyles({
    surface: {
        width: "min(92vw, 560px)",
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
    },
    options: {
        display: "flex",
        flexDirection: "column",
    },
    downloadLinks: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
        paddingTop: tokens.spacingVerticalS,
        "& a": {
            height: "32px",
            display: "inline-flex",
            alignItems: "center",
            lineHeight: 0,
            overflow: "hidden",
        },
    },
    appStoreBadgeImage: {
        height: "32px",
        width: "auto",
    },
    playStoreBadgeImage: {
        height: "46px",
        width: "auto",
    },
    enrollment: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: tokens.spacingVerticalM,
        textAlign: "center",
    },
    qrCode: {
        width: "220px",
        maxWidth: "100%",
        aspectRatio: "1",
    },
    secret: {
        boxSizing: "border-box",
        width: "100%",
        overflowWrap: "anywhere",
        textAlign: "center",
        fontFamily: "monospace",
        ...shorthands.padding(tokens.spacingVerticalS, tokens.spacingHorizontalM),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke2),
        ...shorthands.borderRadius(tokens.borderRadiusMedium),
        backgroundColor: tokens.colorNeutralBackground2,
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
    },
    methodChange: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalM,
    },
    methodChangeCopy: {
        flexGrow: 1,
        flexBasis: "240px",
    },
    enrollmentSwitch: {
        textAlign: "start",
    },
});
