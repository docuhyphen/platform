import React from "react";
import {Badge, Caption1} from "@fluentui/react-components";
import {ExchangeDetailedDto} from "../../../../models/models.tsx";
import {useExchangeDetailsTabSettingsStyles} from "./ExchangeDetailsTabSettingsStyles.tsx";

interface ExchangeDetailsTabSettingsProps
{
    exchangeDetails: ExchangeDetailedDto;
}

const ExchangeDetailsTabSettings: React.FC<ExchangeDetailsTabSettingsProps> = ({exchangeDetails}) =>
{
    const styles = useExchangeDetailsTabSettingsStyles();

    const renderBooleanSetting = (label: string, value?: boolean) => (
        <div id={`exchange-details-tab-div-2-${label}`}
             className={styles.settingItem}
             key={label}>
            <Badge id={"exchange-details-tab-badge-1"}
                   appearance="tint"
                   color={value ? "success" : "subtle"}
                   size="small">
                {value ? "Yes" : "No"}
            </Badge>
            <Caption1 id={"exchange-details-tab-caption1-3"}>{label}</Caption1>
        </div>
    );

    return (
        <div id={"exchange-details-tab-div-12"}
             className={styles.settingsGrid}>
            {renderBooleanSetting("Allow document addition", exchangeDetails.allowDocumentAddition)}
            {renderBooleanSetting("Allow document deletion", exchangeDetails.allowDocumentDeletion)}
            {renderBooleanSetting("Allow download", exchangeDetails.allowDocumentDownload)}
            {renderBooleanSetting("Allow document update", exchangeDetails.allowDocumentUpdate)}
            {renderBooleanSetting("Allow document upload", exchangeDetails.allowDocumentUpload)}
            {renderBooleanSetting("Require sign-in", exchangeDetails.requestRecipientSignIn)}
            {renderBooleanSetting("Watermark", exchangeDetails.watermark)}
            {renderBooleanSetting("Require MFA", exchangeDetails.requireMfa)}
        </div>
    );
};

export default ExchangeDetailsTabSettings;
