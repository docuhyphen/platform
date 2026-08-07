import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useProfilePictureDialogStyles = makeStyles({
    content: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: tokens.spacingVerticalL,
        paddingTop: tokens.spacingVerticalM,
    },

    previewRing: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        width: "160px",
        height: "160px",
        ...shorthands.borderRadius("50%"),
        ...shorthands.border("2px", "solid", tokens.colorNeutralStroke2),
        ...shorthands.overflow("hidden"),
        backgroundColor: tokens.colorNeutralBackground3,
    },

    previewImage: {
        width: "100%",
        height: "100%",
        objectFit: "cover",
    },

    helperText: {
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
    },

    buttonRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },

    errorText: {
        color: tokens.colorPaletteRedForeground1,
        textAlign: "center",
    },

    hiddenInput: {
        display: "none",
    },
});

