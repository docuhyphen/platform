import {makeStyles} from "@fluentui/react-components";

export const useExchangeDocumentSidebarBodyStyles = makeStyles({
    body: {
        flex: 1,
        minHeight: 0,
        overflow: "auto",
        ":last-child": {paddingBottom: 0},
    },
});
