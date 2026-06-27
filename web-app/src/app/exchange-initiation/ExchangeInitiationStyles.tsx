import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeInitiationStyles = makeStyles({
    sharingDetails: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },
    sharingDetailsInput: {
        flex: 1,
    },
    exchangeInitiationTaps: {
        minHeight: "400px",
        // Phones: the dialog shrinks to viewport width via the global
        // .fui-DialogSurface override in index.css. The tab content has
        // its own min-height that's fine, but we relax the min-width and
        // allow the inner panels to wrap to avoid horizontal overflow.
        "@media (max-width: 768px)": {
            minHeight: "auto",
            width: "100%",
            minWidth: 0,
        },
    },
    exchangeDocumentsTabContent: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    exchangeDocumentsRestriction: {
        display: "flex",
        gap: "8px",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        flexWrap: "wrap",
        // Phones: stack the Restrict-type switch, the type Dropdown,
        // and the Required checkbox vertically so each control gets
        // the full row width instead of being squeezed off-screen.
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "stretch",
            gap: "12px",
        },
    },
    exchangeDocumentsRestrictionField: {
        flex: 1,
        display: "flex",
        alignItems: "center",
        gap: "8px",
        minWidth: 0,
        // Phones: switch + dropdown stack rather than sit side-by-side,
        // matching the parent's vertical layout.
        "@media (max-width: 768px)": {
            flexDirection: "column",
            alignItems: "stretch",
            gap: "8px",
        },
    },
    exchangeDocumentsDropdown: {
        marginRight: "36px",
        minWidth: "100px",
        // Phones: take full available width and drop the right margin
        // (which was there to visually separate it from the sibling
        // Required checkbox in the horizontal layout).
        "@media (max-width: 768px)": {
            marginRight: 0,
            width: "100%",
            minWidth: 0,
        },
    },
    shadingExchangeDocumentCard: {
        flex: 1,
    },
    dialogTitle: {
        display: "flex",
        gap: "16px",
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
        gap: "16px",
        flexDirection: "column",
    },
    sharingOptionsTapContent: {
        display: "flex",
        gap: "16px",
        flexDirection: "column",
    },
    exchangeInitiationSuccess: {
        minHeight: "200px",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        flexDirection: "column",
        gap: "16px",
    },
    exchangeSuccessDetails: {
        display: "grid",
        gridTemplateColumns: "auto 1fr",
        columnGap: "12px",
        rowGap: "8px",
        width: "100%",
        maxWidth: "440px",
        padding: "12px",
        borderRadius: tokens.borderRadiusMedium,
        backgroundColor: tokens.colorNeutralBackground2,
    },
    exchangeSuccessActions: {
        display: "flex",
        gap: "8px",
        alignItems: "center",
        marginBottom: "1rem"
    },
    errorMessagesGroup: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },
    iconDeleteFilled: {
        color: tokens.colorPaletteRedForeground1,
    },
    addDocumentButtonContainer: {
        display: "flex",
        justifyContent: "center",
        marginBottom: "1rem",
        gap: "1rem",
    },
    dialogContentContainer: {
        minHeight: "460px",
        // Phones: don't force a 460px min-height (some phones in
        // landscape barely have that vertical room) and never allow the
        // form to push the dialog wider than the viewport.
        "@media (max-width: 768px)": {
            minHeight: "auto",
            minWidth: 0,
            width: "100%",
        },
    },

    /**
     * Mobile-only: the selected tab's full label rendered above the tab
     * content. On phone viewports the tabs themselves collapse to
     * icon-only buttons; this title keeps the user oriented.
     */
    mobileSelectedTabTitle: {
        display: "block",
        marginTop: "4px",
    },
    tagInput: {
        display: "flex",
        flexWrap: "wrap",
        gap: "4px",
        alignItems: "center",
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        padding: "4px 8px",
        minHeight: "32px",
        cursor: "text",
    },
    variableOverridesPanel: {
        padding: "12px 16px",
        border: "1px solid var(--colorBrandStroke2)",
        borderRadius: tokens.borderRadiusXLarge,
        background: "var(--colorNeutralBackground2)",
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        marginBottom: "12px",
    },
    variableOverridesSubtext: {
        color: "var(--colorNeutralForeground3)",
    },
    variableOverridesRow: {
        display: "flex",
        gap: "8px",
        alignItems: "center",
    },
    variableOverridesLabel: {
        minWidth: "120px",
        fontFamily: "monospace",
    },
    variableOverridesInput: {
        flex: 1,
        padding: "4px 8px",
        borderRadius: tokens.borderRadiusMedium,
        border: "1px solid var(--colorNeutralStroke1)",
        background: "var(--colorNeutralBackground1)",
        color: "inherit",
    },
    saveBlueprintBackRow: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
    },
    blueprintPickerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
        minHeight: "300px",
    },
    blueprintPickerStickyHeader: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        paddingBottom: "8px",
    },
    blueprintPickerControls: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
    },
    blueprintPickerSearchRow: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
        width: "100%",
    },
    blueprintPickerActiveTagsRow: {
        display: "flex",
        flexWrap: "wrap",
        alignItems: "center",
        gap: "4px",
    },
    blueprintPickerFilterPopover: {
        padding: "8px",
        display: "flex",
        flexDirection: "column",
        gap: "6px",
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
    blueprintPickerSpinnerWrapper: {
        display: "flex",
        justifyContent: "center",
        padding: "24px",
    },
    blueprintPickerErrorText: {
        color: "var(--colorPaletteRedForeground1)",
    },
    blueprintPickerEmptyText: {
        color: "var(--colorNeutralForeground3)",
    },
    blueprintList: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        overflowY: "auto",
    },
    blueprintPickerPaginationRow: {
        display: "flex",
        justifyContent: "center",
        paddingTop: "4px",
    },
    blueprintCard: {
        border: "1px solid var(--colorNeutralStroke1)",
        borderRadius: tokens.borderRadiusLarge,
        padding: "12px 16px",
        display: "flex",
        flexDirection: "column",
        gap: "6px",
    },
    blueprintCardHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
    },
    blueprintCardSummary: {
        color: "var(--colorNeutralForeground2)",
    },
    blueprintTagRow: {
        display: "flex",
        gap: "4px",
        flexWrap: "wrap",
    },
    saveBlueprintContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "16px",
    },
    saveBlueprintErrorText: {
        color: "var(--colorPaletteRedForeground1)",
        fontSize: "12px",
    },
    saveBlueprintActions: {
        display: "flex",
        gap: "8px",
        paddingTop: "4px",
    },
    docLinkedBadgeRow: {
        display: "flex",
        alignItems: "center",
        gap: "8px",
        marginTop: "6px",
    },
    choosingBlueprintSubtext: {
        color: "var(--colorNeutralForeground3)",
    },
    docPickerContainer: {
        display: "flex",
        flexDirection: "column",
        gap: "12px",
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
        padding: "24px",
    },
    docPickerErrorText: {
        color: "var(--colorPaletteRedForeground1)",
    },
    docPickerEmptyText: {
        color: "var(--colorNeutralForeground3)",
    },
    docPickerList: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        overflowY: "auto",
    },
    docPickerEntryHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
    },
    docPickerEntryDescription: {
        color: "var(--colorNeutralForeground2)",
    },
    docPickerEntryTagRow: {
        display: "flex",
        gap: "4px",
        flexWrap: "wrap",
    },
});