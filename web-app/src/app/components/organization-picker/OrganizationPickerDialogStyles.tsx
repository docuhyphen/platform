import {tokens, makeStyles} from "@fluentui/react-components";

export const useOrganizationPickerDialogStyles = makeStyles({
    content: {
        margin: `${tokens.spacingVerticalS} 0`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    orgList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        marginTop: tokens.spacingVerticalS,
        width: "100%",
    },
    orgButton: {
        justifyContent: "flex-start",
        width: "100%",
    },
});
