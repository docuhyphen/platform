import {makeStyles} from '@fluentui/react-components';

export const useSessionAcceptanceDialogStyles = makeStyles({
    dialogContent: {
        display: "flex",
        flexDirection: "column",
        gap: "24px",
        margin: "24px 0"
    },
    messageContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        flexGrow: 1
    },
    sessionNameContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },
    declineFieldContainer: {
        width: "100%",
        display: "flex",
        flexDirection: "column",
    },
    declineField: {
        width: "100%"
    }
});
