import {makeStyles, tokens} from "@fluentui/react-components";

export const useSettingsStyles = makeStyles({
    container: {
        alignItems: "flex-start",
        display: "flex",
        flexDirection: "column",
        padding: "16px 60px",
        maxWith: "100%",
        width: "980px",
        flex: "1",
        rowGap: "20px",
        margin: "auto",
        marginTop: "80px",
        minHeight: "480px",
        boxShadow: tokens.shadow4,
        background: tokens.colorNeutralBackground1,
        height: "calc(100% - 140px)"
    },
    tabs: {
        width: "100%"
    },
});