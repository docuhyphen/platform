import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeInitiationStyles = makeStyles({
    sharingDetails: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },
    sharingDetailsInput: {
        flex: 1,
    },
    exchangeDocumentsTabContent: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        flexDirection: "column",
    },
    exchangeDocumentsRestriction: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "stretch",
            gap: tokens.spacingHorizontalM,
        },
    },
    exchangeDocumentsRestrictionField: {
        flex: 1,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "stretch",
            gap: tokens.spacingHorizontalS,
        },
    },
    exchangeDocumentsDropdown: {
        marginRight: tokens.spacingHorizontalXXXL,
        minWidth: "100px",
        "@media (max-width: 768px)": {
            marginRight: 0,
            width: "100%",
            minWidth: 0,
        },
    },
    shadingExchangeDocumentCard: {
        flex: 1,
    },

    dialog: {
      width: "46vw",
      maxWidth: "100%"
    },

    dialogTitle: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        flexDirection: "column",
    },
    dialogContent: {
        paddingTop: "0"
    },
    dialogTitle1: {
        display: "flex",
        justifyContent: "space-between",
    },
    exchangeDetailsTap: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        flexDirection: "column",
    },
    sharingOptionsTapContent: {
        display: "flex",
        gap: tokens.spacingHorizontalL,
        flexDirection: "column",
    },
    iconDeleteFilled: {
        color: tokens.colorPaletteRedForeground1,
    },
    addDocumentButtonContainer: {
        display: "flex",
        justifyContent: "center",
        marginBottom: tokens.spacingVerticalL,
        gap: tokens.spacingHorizontalL,
    },
    dialogContentContainer: {
        minHeight: "460px",
        "@media (max-width: 768px)": {
            minHeight: "auto",
            minWidth: 0,
            width: "100%",
        },
    },

    mobileSelectedTabTitle: {
        display: "block",
        marginTop: tokens.spacingVerticalXS,
    },
    exchangeInitiationTabsViewport: {
        width: "100%",
        minWidth: 0,
        overflowX: "auto",
        overflowY: "hidden",
        paddingBottom: tokens.spacingVerticalXS,
        scrollbarWidth: "none",
        "&::-webkit-scrollbar": {
            display: "none",
        },
    },
    exchangeInitiationTabsList: {
        display: "flex",
        justifyContent: "flex-start",
        minWidth: "max-content",
    },
    tagInput: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        minHeight: "32px",
        cursor: "text",
    },
    blueprintPickerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        minHeight: "300px",
    },
    blueprintPickerStickyHeader: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
    },
    blueprintPickerControls: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },
    blueprintPickerSearchRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        width: "100%",
    },
    blueprintPickerSearchField: {
        flex: 1,
    },
    blueprintPickerActiveTagsRow: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
    },
    blueprintPickerFilterPopover: {
        padding: tokens.spacingHorizontalS,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        minWidth: "180px",
        maxWidth: "260px",
    },
    blueprintPickerFilterPopoverList: {
        minHeight: "80px",
        maxHeight: "200px",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
    },
    blueprintPickerFilterEmptyText: {
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        color: tokens.colorNeutralForeground3,
    },
    blueprintPickerSpinnerWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXXL,
    },
    blueprintPickerErrorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    blueprintPickerEmptyText: {
        color: tokens.colorNeutralForeground3,
    },
    blueprintList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        overflowY: "auto",
    },
    blueprintPickerPaginationRow: {
        display: "flex",
        justifyContent: "center",
        paddingTop: tokens.spacingVerticalXS,
    },
    blueprintCard: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
    },
    blueprintCardHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
    },
    blueprintCardSummary: {
        color: tokens.colorNeutralForeground2,
    },
    blueprintTagRow: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
    },
    saveBlueprintContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
    },
    saveBlueprintErrorText: {
        color: tokens.colorPaletteRedForeground1,
        fontSize: tokens.fontSizeBase200,
    },
    saveBlueprintActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        justifyContent: "flex-end",
        paddingTop: tokens.spacingVerticalXS,
    },
    docLinkedBadgeRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        marginTop: tokens.spacingVerticalSNudge,
    },
    choosingBlueprintSubtext: {
        color: tokens.colorNeutralForeground3,
    },
    docPickerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        minHeight: "300px",
    },
    docPickerHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
    },
    docPickerSpinnerWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXXL,
    },
    docPickerErrorText: {
        color: tokens.colorPaletteRedForeground1,
    },
    docPickerEmptyText: {
        color: tokens.colorNeutralForeground3,
    },
    docPickerList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        overflowY: "auto",
    },
    docPickerEntry: {
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusXLarge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        cursor: "pointer",
    },
    docPickerEntrySelected: {
        borderTopColor: tokens.colorBrandStroke1,
        borderRightColor: tokens.colorBrandStroke1,
        borderBottomColor: tokens.colorBrandStroke1,
        borderLeftColor: tokens.colorBrandStroke1,
        backgroundColor: tokens.colorBrandBackground2,
    },
    docPickerEntryHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
    },
    docPickerEntryDescription: {
        color: tokens.colorNeutralForeground2,
    },
    docPickerEntryTagRow: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
    },
});
