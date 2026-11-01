import React from 'react';
import {Button, Text} from "@fluentui/react-components";
import {ArrowLeftRegular} from "@fluentui/react-icons";
import {useExchangeInitiationSaveBlueprintTitleStyles} from "./ExchangeInitiationSaveBlueprintTitleStyles.tsx";

interface ExchangeInitiationSaveBlueprintTitleProps
{
    onBack: () => void;
}

const ExchangeInitiationSaveBlueprintTitle: React.FC<ExchangeInitiationSaveBlueprintTitleProps> = ({onBack}) =>
{
    const styles = useExchangeInitiationSaveBlueprintTitleStyles();

    return (
        <div id={"exchange-initiation-save-blueprint-back-row"}
             className={styles.saveBlueprintBackRow}>
            <Button id={"exchange-save-blueprint-back-btn"}
                    appearance="subtle"
                    shape="circular"
                    size="small"
                    icon={<ArrowLeftRegular/>}
                    onClick={onBack}
                    aria-label="Back to Exchange"/>
            <Text id={"exchange-initiation-save-as-blueprint-text"}
                  weight="semibold"
                  size={500}>Save as Blueprint</Text>
        </div>
    );
};

export default ExchangeInitiationSaveBlueprintTitle;
