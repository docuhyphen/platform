// web-app/doc-hyphen/src/app/sharing-sessions/components/session-document-comments/SessionDocumentCommentsStyles.tsx
import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useSessionDocumentVersionsStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        gap: tokens.spacingVerticalM,
    },
    header: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        ...shorthands.padding(tokens.spacingVerticalS, 0)
    },
    uploadContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        ...shorthands.padding(tokens.spacingVerticalM),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke1),
        ...shorthands.borderRadius(tokens.borderRadiusMedium),
        marginBottom: tokens.spacingVerticalM
    },
    uploadInput: {
        display: "none"
    },
    buttonContainer: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalM,
        justifyContent: "space-between",
        alignItems: "center"
    },
    noVersions: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "200px",
        ...shorthands.gap(tokens.spacingVerticalL),
        color: tokens.colorNeutralForeground3
    },
    versionList: {
        overflowY: "auto",
        flexGrow: 1
    },
    fileLabel: {
        ...shorthands.overflow("hidden"),
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
        maxWidth: "180px"
    }
});