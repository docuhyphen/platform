import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useExchangeDocumentVersionsStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        gap: tokens.spacingVerticalM,
    },
    versionList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        overflowY: "auto",
        flexGrow: 1,
        minHeight: 0,
    },
    uploadButtonRow: {
        display: "flex",
        justifyContent: "flex-end",
        alignItems: "center",
        ...shorthands.padding(tokens.spacingVerticalS, 0),
        borderTop: `1px solid ${tokens.colorNeutralStroke1}`,
        flexShrink: 0,
    },
    noVersions: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "200px",
        ...shorthands.gap(tokens.spacingVerticalL),
        color: tokens.colorNeutralForeground3,
    },
    errorText: {
        color: tokens.colorStatusDangerForeground1,
    },
});
