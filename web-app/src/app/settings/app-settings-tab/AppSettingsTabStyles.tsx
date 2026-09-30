import {makeStyles, tokens} from "@fluentui/react-components";

export const useAppSettingsTabStyles = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        width: "100%",
        minWidth: 0,
    },
    mainDivider: {
        width: "300px",
    },
    notificationList: {
        display: "flex",
        flexDirection: "column",
    },
});
