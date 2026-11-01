import React from "react";
import NoAuthExchangeHeader from "./components/header/NoAuthExchangeHeader.tsx";
import {useNoAuthExchangeStyles} from "./NoAuthExchangeStyles.tsx";
import NoAuthExchangeUserDecision from "./components/exchange-use-decision/NoAuthExchangeUserDecision.tsx";
import {FluentProvider, Spinner} from "@fluentui/react-components";
import NoAuthExchangeWorkspace from "./components/exchange-workspace/NoAuthExchangeWorkspace.tsx";
import {lightTheme} from "../../context/theme.ts";
import useNoAuthExchange from "./useNoAuthExchange.ts";

const NoAuthExchange: React.FC = () =>
{
    const styles = useNoAuthExchangeStyles();
    const {
        isLoadingExchange,
        exchange,
        exchangeAccepted,
        onExchangeAccepted,
        onExchangeDeclined,
        onDocumentUploaded,
    } = useNoAuthExchange();

    return (
        <FluentProvider
            id={"no-auth-exchange-light-theme-provider"}
            theme={lightTheme}
            className={styles.themeProvider}
        >
            <section
                id={"no-auth-exchange-page"}
                className={styles.container}
            >
                <NoAuthExchangeHeader/>

                {isLoadingExchange && (
                    <div
                        id={"no-auth-exchange-loading"}
                        className={styles.exchangeLoadingContainer}
                    >
                        <Spinner
                            id={"no-auth-exchange-spinner-1"}
                            size={"medium"}
                            label={"Preparing your secure document request"}
                        />
                    </div>
                )}

                {!isLoadingExchange && exchange && (
                    <>
                        {exchangeAccepted ? (
                            <NoAuthExchangeWorkspace exchange={exchange}
                                                     onDocumentUploaded={onDocumentUploaded}/>
                        ) : (
                            <section
                                id={"no-auth-exchange-decision"}
                                className={styles.exchangeDecisionContainer}
                            >
                                <NoAuthExchangeUserDecision
                                    exchange={exchange}
                                    onAccepted={onExchangeAccepted}
                                    onDeclined={onExchangeDeclined}
                                />
                            </section>
                        )}
                    </>
                )}
            </section>
        </FluentProvider>
    );
};

export default NoAuthExchange;
