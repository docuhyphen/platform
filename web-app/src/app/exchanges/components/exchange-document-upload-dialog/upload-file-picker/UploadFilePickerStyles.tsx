import {makeStyles, tokens} from "@fluentui/react-components";

export const useUploadFilePickerStyles = makeStyles({
    uploadContainer: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalS} 0`,
        flexWrap: "wrap",
    },

    hiddenInput: {
        display: "none",
    },
});
