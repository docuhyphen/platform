import React from 'react';
import {
    Button,
    TabValue,
    Text,
} from "@fluentui/react-components";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {BlueprintAddIcon} from "../../../components/IconBundles.tsx";
import ExchangeInitiationDialogTabs from "./ExchangeInitiationDialogTabs.tsx";

interface DialogTitleSectionProps
{
    exchangeInitiatedSuccessfully: boolean;
    choosingBlueprint: boolean;
    requestingDocuments: boolean;
    selectedBlueprintName?: string | null;
    selectedTab: TabValue;
    showFieldsTab?: boolean;
    onTabSelect: (value: TabValue) => void;
    onSaveAsBlueprint?: () => void;
}

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
                <ExchangeInitiationDialogTabs selectedTab={selectedTab}
                                              showFieldsTab={showFieldsTab}
                                              onTabSelect={onTabSelect}/>
            }
        </>
    );
};

export default ExchangeInitiationDialogTitleSection;
