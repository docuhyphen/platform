import React from "react";
import {TabValue} from "@fluentui/react-components";
import {
    DetailsIcon,
    DocumentsIcon,
    OptionsIcon,
    RecipientsIcon,
    SettingsFieldsTabIcon,
} from "../../../components/IconBundles.tsx";

export const TAB_LABELS: Record<string, string> = {
    "recipients-tab": "Recipients & Participants",
    "details-tab": "Details",
    "fields-tab": "Business Fields",
    "documents-tab": "Documents",
    "options-tab": "Options",
};

export type ExchangeInitiationTabConfig = {
    id: string;
    value: TabValue;
    label: string;
    icon: React.ReactNode;
};

export const buildExchangeInitiationTabs = (showFieldsTab?: boolean): ExchangeInitiationTabConfig[] => [
    {id: "recipients", value: "recipients-tab", label: TAB_LABELS["recipients-tab"], icon: <RecipientsIcon/>},
    {id: "details", value: "details-tab", label: TAB_LABELS["details-tab"], icon: <DetailsIcon/>},
    ...(showFieldsTab
        ? [{id: "fields", value: "fields-tab", label: TAB_LABELS["fields-tab"], icon: <SettingsFieldsTabIcon/>}]
        : []),
    {id: "documents", value: "documents-tab", label: TAB_LABELS["documents-tab"], icon: <DocumentsIcon/>},
    {id: "options", value: "options-tab", label: TAB_LABELS["options-tab"], icon: <OptionsIcon/>},
];
