import {makeStyles} from "@fluentui/react-components";

export const useDownloadFormatRestrictionStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        marginLeft: "24px",
        marginTop: "4px",
    },
    formatCheckboxes: {
        display: "flex",
        flexWrap: "wrap",
        gap: "4px",
        marginLeft: "24px",
    },
});
