import React from 'react';
import {Text} from '@fluentui/react-components';
import {useExchangeSelectionHintStyles} from './ExchangeSelectionHintStyles.tsx';

const ExchangeSelectionHint: React.FC = () =>
{
    const styles = useExchangeSelectionHintStyles();

    return (
        <div id={"exchanges-no-exchange-selected-section-5"}
             className={styles.noExchangeSelectedSection}>
            <Text id={"exchanges-select-a-exchange-in-text"}
                  size={500}> Select a Exchange in the list to view details</Text>
        </div>
    );
};

export default ExchangeSelectionHint;
