import {makeStyles, tokens} from "@fluentui/react-components";

export const useParingRequestDialogStyles = makeStyles({
    dialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: "24px",
        margin: "24px 0"
    },

    tagsList: {
        listStyleType: "none",
        marginBottom: tokens.spacingVerticalXXS,
        marginTop: 0,
        paddingLeft: 0,
        display: "flex",
        gridGap: tokens.spacingHorizontalXXS,
        flexWrap: "wrap"
    },

    field: {
        marginBottom: tokens.spacingVerticalM
    }
});