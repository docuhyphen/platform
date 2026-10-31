import {makeStyles, tokens} from "@fluentui/react-components";

export const useRespondentLinkNoticeStyles = makeStyles({
    centered: {
        minHeight: "60vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
    },
    error: {
        color: tokens.colorPaletteRedForeground1,
    },
});
