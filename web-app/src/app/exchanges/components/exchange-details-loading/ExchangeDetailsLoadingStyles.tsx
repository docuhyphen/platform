import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsLoadingStyles = makeStyles({

    container: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
        flex: 1,
        // Allow the loading view to shrink to fit the master-detail
        // mobile pane (which is the full viewport). Without this, the
        // fixed pixel widths of the inner skeleton bars push the layout
        // past the screen edge on phones.
        minWidth: 0,
        width: "100%",
        boxSizing: "border-box",
    },

    heading: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        width: "100%",
        padding: "8px 16px",
        boxSizing: "border-box",
        borderRadius: "4px",
        minWidth: 0,
        // Match the real ExchangeDetailsHeader's tighter mobile padding so
        // the skeleton occupies the same vertical footprint as the loaded
        // header on phones.
        "@media (max-width: 768px)": {
            padding: "6px 8px",
            gap: "4px",
        },
    },

    // Kept as a class so the actions-cluster collapse-toggle skeleton
    // can be targeted later if its shape needs to differ from the other
    // square action placeholders. Currently it shares the 32px square
    // shape of its siblings.
    collapseIcon: {
        flexShrink: 0,
    },

    headerLine2: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        justifyContent: "space-between",
        alignItems: "center",
        minWidth: 0,
    },

    name: {
        // Was 300px fixed - now flex to fill the available row and
        // never push the surrounding action skeletons off-screen.
        flex: 1,
        minWidth: "80px",
        maxWidth: "300px",
    },

    exchangeActions: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        flexShrink: 0,
        "@media (max-width: 768px)": {
            gap: "4px",
        },
    },

    exchangeActionsMore: {
        width: "8px",
    },


    documentSearch: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        minWidth: 0,
        width: "100%",
        boxSizing: "border-box",
    },

    documentSearchInput: {
        // Was 300px fixed; flex to fit any container.
        flex: 1,
        minWidth: 0,
        maxWidth: "300px",
    },

    documentCardListContainer: {
        overflow: "hidden",
        boxSizing: "border-box",
        width: "100%",
    },

    documentCardList: {
        display: "flex",
        flexDirection: "row",
        gap: "8px",
        padding: "2px 2px",
        // Phones: stack cards so each card uses the full row width
        // instead of trying to fit two 230px-min-wide cards on a
        // ~360px viewport (which produced horizontal overflow).
        "@media (max-width: 768px)": {
            flexDirection: "column",
        },
    },

    documentCard: {
        minWidth: "230px",
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        gap: "8px",
        height: "70px",
        flex: 1,
        boxSizing: "border-box",
        "@media (max-width: 768px)": {
            minWidth: 0,
            width: "100%",
        },
    },

    documentTitle: {
        width: "150px",
        maxWidth: "60%",
    },

    documentUploadDate: {
        width: "100px",
        maxWidth: "40%",
        marginTop: "8px",
    },

    documentMoreOptions: {
        width: "8px",
        flexShrink: 0,
    },

    pdfPreviewSection: {
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "4px",
        margin: "auto",
        width: "100%",
        maxWidth: "500px",
        flex: 1,
        // Phones: take the full available width since the master-detail
        // pane IS the entire viewport - capping at 500px would leave
        // visible empty bands on either side.
        "@media (max-width: 768px)": {
            maxWidth: "100%",
        },
    },
});