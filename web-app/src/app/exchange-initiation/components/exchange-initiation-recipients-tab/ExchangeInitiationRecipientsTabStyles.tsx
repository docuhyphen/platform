import {makeStyles, shorthands} from "@fluentui/react-components";

export const useExchangeInitiationRecipientsTabStyles = makeStyles({

    recipientsTabContent: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },

    recipientEmailFields: {
        display: "flex",
        gap: "8px",
    },
    recipientEmail: {
        flex: 1
    }
});