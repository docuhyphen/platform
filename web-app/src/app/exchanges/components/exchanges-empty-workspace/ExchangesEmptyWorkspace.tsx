import React from 'react';
import {Text} from '@fluentui/react-components';
import EmptyStateIllustration from '../empty-state-illustration/EmptyStateIllustration.tsx';
import {useExchangesEmptyWorkspaceStyles} from './ExchangesEmptyWorkspaceStyles.tsx';

const ExchangesEmptyWorkspace: React.FC = () =>
{
    const styles = useExchangesEmptyWorkspaceStyles();

    return (
        <div id={"exchanges-container-no-exchanges"}
             className={styles.containerNoExchanges}>
            <EmptyStateIllustration className={styles.noExchangesIllustration}/>
            <Text id={"exchanges-share-your-first-document-text"}
                  size={600}
                  weight={"semibold"}
                  align={"center"}>
                Share your first document
            </Text>
            <Text id={"exchanges-send-or-receive-documents-text"}
                  size={300}
                  align={"center"}>
                Send or receive documents securely to anyone, your Exchanges will appear here.
                Tap <Text id={"exchanges-start-exchange-text"}
                          italic
                          weight={"semibold"}>Start Exchange</Text> in the main menu to begin.
            </Text>
        </div>
    );
};

export default ExchangesEmptyWorkspace;
