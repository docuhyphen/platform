import {Tab, TabList, TabValue, Text} from "@fluentui/react-components";
import {useEffect, useRef} from "react";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import {
    buildExchangeInitiationTabs,
    TAB_LABELS,
} from "./exchangeInitiationDialogTabsConfig.tsx";

interface ExchangeInitiationDialogTabsProps
{
    selectedTab: TabValue;
    showFieldsTab?: boolean;
    onTabSelect: (value: TabValue) => void;
}

const ExchangeInitiationDialogTabs = (
    {
        selectedTab,
        showFieldsTab,
        onTabSelect,
    }: ExchangeInitiationDialogTabsProps) =>
{
    const styles = useExchangeInitiationStyles();
    const isMobile = useIsMobile();
    const tabViewportRef = useRef<HTMLDivElement>(null);
    const tabRefs = useRef<Record<string, HTMLElement | null>>({});
    const selectedTabLabel = TAB_LABELS[String(selectedTab)] ?? "";
    const tabs = buildExchangeInitiationTabs(showFieldsTab);

    useEffect(() =>
    {
        const tabViewport = tabViewportRef.current;
        const activeTab = tabRefs.current[String(selectedTab)];
        if (!tabViewport || !activeTab) return;

        const targetScrollLeft = activeTab.offsetLeft - ((tabViewport.clientWidth - activeTab.clientWidth) / 2);
        tabViewport.scrollTo({
            left: Math.max(0, targetScrollLeft),
            behavior: "smooth",
        });
    }, [selectedTab, showFieldsTab]);

    return (
        <>
            <div id={"exchange-initiation-tabs-viewport"}
                 className={styles.exchangeInitiationTabsViewport}
                 ref={tabViewportRef}>
                <TabList id={"exchange-initiation-tabs"}
                         className={styles.exchangeInitiationTabsList}
                         selectedValue={selectedTab}
                         onTabSelect={(_, data) => onTabSelect(data.value)}>
                    {tabs.map(tab => (
                        <Tab key={String(tab.value)}
                             id={tab.id}
                             icon={tab.icon}
                             value={tab.value}
                             aria-label={isMobile ? tab.label : undefined}
                             title={isMobile ? tab.label : undefined}
                             ref={(element) =>
                             {
                                 tabRefs.current[String(tab.value)] = element;
                             }}>
                            {isMobile ? undefined : tab.label}
                        </Tab>
                    ))}
                </TabList>
            </div>
            {isMobile && selectedTabLabel && (
                <Text size={400} weight={"semibold"} className={styles.mobileSelectedTabTitle}>
                    {selectedTabLabel}
                </Text>
            )}
        </>
    );
};

export default ExchangeInitiationDialogTabs;
