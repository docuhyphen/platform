import {makeStyles, tokens} from "@fluentui/react-components";

export const useSettingsStyles = makeStyles({
    container: {
        alignItems: "flex-start",
        display: "flex",
        flexDirection: "column",
        justifyContent: "flex-start",
        padding: "80px 60px",
        maxWidth: "300px",
        rowGap: "20px",
    },
    panels: {
        padding: "0 10px",
        "& th": {
            textAlign: "left",
            padding: "0 30px 0 0",
        },
    },
    propsTable: {
        "& td:first-child": {
            fontWeight: tokens.fontWeightSemibold,
        },
        "& td": {
            padding: "0 30px 0 0",
        },
    },
});