import React from 'react';
import {Text} from "@fluentui/react-components";
import {useExchangeInitiationVariableOverridesPanelStyles} from "./ExchangeInitiationVariableOverridesPanelStyles.tsx";

interface ExchangeInitiationVariableOverridesPanelProps
{
    tokens: string[];
    overrides: Record<string, string>;
    onOverrideChange: (token: string, value: string) => void;
}

const ExchangeInitiationVariableOverridesPanel: React.FC<ExchangeInitiationVariableOverridesPanelProps> = (
    {
        tokens,
        overrides,
        onOverrideChange,
    }) =>
{
    const styles = useExchangeInitiationVariableOverridesPanelStyles();

    if (tokens.length === 0) return null;

    return (
        <div id={"exchange-initiation-variable-overrides-panel"}
             className={styles.variableOverridesPanel}>
            <Text id={"exchange-initiation-fill-in-variables-text"}
                  weight="semibold"
                  size={300}>Fill in variables</Text>
            <Text id={"exchange-initiation-variable-overrides-subtext"}
                  size={200}
                  className={styles.variableOverridesSubtext}>
                Override the default values for this Exchange.
            </Text>
            {tokens.map(token => (
                <div id={`exchange-initiation-variable-overrides-row-${token}`}
                     key={token}
                     className={styles.variableOverridesRow}>
                    <Text id={`exchange-initiation-variable-overrides-label-${token}`}
                          size={200}
                          className={styles.variableOverridesLabel}>{`{{${token}}}`}</Text>
                    <input id={`exchange-initiation-variable-overrides-input-${token}`}
                           className={styles.variableOverridesInput}
                           value={overrides[token] ?? ''}
                           onChange={e => onOverrideChange(token, e.target.value)}
                           placeholder={`Value for ${token}`}/>
                </div>
            ))}
        </div>
    );
};

export default ExchangeInitiationVariableOverridesPanel;
