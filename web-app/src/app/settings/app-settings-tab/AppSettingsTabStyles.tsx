import {makeStyles, tokens} from "@fluentui/react-components";

export const useAppSettingsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        width: "100%",
        maxWidth: "760px",
        minWidth: 0,
    },
    mainDivider: {
        width: "100%",
    },
    notificationList: {
        display: "flex",
        flexDirection: "column",
    },
});
