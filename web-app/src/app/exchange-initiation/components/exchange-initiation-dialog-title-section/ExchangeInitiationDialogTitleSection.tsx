import React from 'react';
import {Button, SelectTabData, SelectTabEvent, Tab, TabList, TabValue, Text, Tooltip} from "@fluentui/react-components";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {
    BlueprintAddIcon,
    DetailsIcon,
    DocumentsIcon,
    OptionsIcon,
    RecipientsIcon,
    SettingsFieldsTabIcon
} from "../../../components/IconBundles.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";

interface DialogTitleSectionProps
{
    exchangeInitiatedSuccessfully: boolean;
    choosingBlueprint: boolean;
    requestingDocuments: boolean;
    selectedBlueprintName?: string | null;
    selectedTab: TabValue;
    showFieldsTab?: boolean;
    onTabSelect: (event: SelectTabEvent, data: SelectTabData) => void;
    onSaveAsBlueprint?: () => void;
}

/**
 * Labels for each tab. Used in two places:
 * - As the visible tab label on tablet/desktop.
 * - As an h-level title rendered ABOVE the tab content on mobile, where
 *   the tabs themselves collapse to icon-only buttons to fit narrow
 *   viewports (long labels like "Recipients & Participants" would
 *   otherwise overflow horizontally with no scroll affordance).
 */
const TAB_LABELS: Record<string, string> = {
    "recipients-tab": "Recipients & Participants",
    "details-tab": "Details",
    "fields-tab": "Business Fields",
    "documents-tab": "Documents",
    "options-tab": "Options",
};

const ExchangeInitiationDialogTitleSection: React.FC<DialogTitleSectionProps> = (
    {
        exchangeInitiatedSuccessfully,
        choosingBlueprint,
        requestingDocuments,
        selectedBlueprintName,
        selectedTab,
        showFieldsTab,
        onTabSelect,
        onSaveAsBlueprint,
    }) =>
{
    const styles = useExchangeInitiationStyles();
    const isMobile = useIsMobile();

    const selectedTabLabel = TAB_LABELS[String(selectedTab)] ?? '';

    const renderTab = (id: string, value: string, icon: React.ReactNode) =>
    {
        const label = TAB_LABELS[value];
        // Mobile: render only the icon (with a tooltip so the label is
        // still discoverable). Desktop/tablet: render icon + label.
        if (isMobile)
        {
            return (
                <Tooltip content={label} relationship="label">
                    <Tab id={id} icon={icon} value={value} aria-label={label}/>
                </Tooltip>
            );
        }
        return (
            <Tab id={id} icon={icon} value={value}>{label}</Tab>
        );
    };

    return (
        <>
            <div className={styles.dialogTitle1}>
                {!exchangeInitiatedSuccessfully &&
                    <Text size={500}>
                        {choosingBlueprint
                            ? 'Choose a Blueprint'
                            : selectedBlueprintName
                                ? selectedBlueprintName
                                : (requestingDocuments ? 'Request Documents' : 'Send Documents')
                        }
                    </Text>
                }
                {(!choosingBlueprint && !exchangeInitiatedSuccessfully && onSaveAsBlueprint) &&
                    <Button id={"exchange-initiation-save-blueprint-btn"}
                            appearance={"subtle"}
                            shape={"circular"}
                            size={"small"}
                            icon={<BlueprintAddIcon/>}
                            onClick={onSaveAsBlueprint}>
                        Save as Blueprint
                    </Button>
                }
            </div>
            {choosingBlueprint && (
                <Text size={300} className={styles.choosingBlueprintSubtext}>
                    Select a blueprint to pre-fill the Exchange form
                </Text>
            )}
            {(!choosingBlueprint && !exchangeInitiatedSuccessfully) &&
                <>
                    <TabList selectedValue={selectedTab} onTabSelect={onTabSelect}>
                        {renderTab("recipients", "recipients-tab", <RecipientsIcon/>)}
                        {renderTab("details", "details-tab", <DetailsIcon/>)}
                        {showFieldsTab && renderTab("fields", "fields-tab", <SettingsFieldsTabIcon/>)}
                        {renderTab("documents", "documents-tab", <DocumentsIcon/>)}
                        {renderTab("options", "options-tab", <OptionsIcon/>)}
                    </TabList>
                    {isMobile && selectedTabLabel && (
                        <Text size={400} weight={"semibold"} className={styles.mobileSelectedTabTitle}>
                            {selectedTabLabel}
                        </Text>
                    )}
                </>
            }
        </>
    );
};

export default ExchangeInitiationDialogTitleSection;
