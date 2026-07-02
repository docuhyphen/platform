import React, {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {NoAuthExchangeBasicDto, ExchangeStatus, DocumentDetailedDto} from "../models/models.tsx";
import NoAuthExchangeHeader from "./components/header/NoAuthExchangeHeader.tsx";
import {useNoAuthExchangeStyles} from "./NoAuthExchangeStyles.tsx";
import NoAuthExchangeUserDecision from "./components/exchange-use-decision/NoAuthExchangeUserDecision.tsx";
import {FluentProvider, Spinner} from "@fluentui/react-components";
import {fetchNoAuthExchange} from "../../services/exchangeApi.ts";
import NoAuthExchangeWorkspace from "./components/exchange-workspace/NoAuthExchangeWorkspace.tsx";
import {lightTheme} from "../../context/theme.ts";

const NoAuthExchange: React.FC = () =>
{
    const styles = useNoAuthExchangeStyles();
    const navigate = useNavigate();

    const [isLoadingExchange, setIsLoadingExchange] = useState(false);
    const [exchangeId, setExchangeId] = useState<string | null>(null);
    const [exchangeAccepted, setExchangeAccepted] = useState(false);
    const [exchange, setExchange] = useState<NoAuthExchangeBasicDto>(null);

    useEffect(() =>
    {
        const queryParams = new URLSearchParams(window.location.search);
        const exchangeIdParam = queryParams.get('s');

        if (!exchangeIdParam)
        {
            navigate('/sign-in');
        }
        else
        {
            setExchangeId(exchangeIdParam);
        }
    }, [navigate]);

    const fetchExchange = async () =>
    {
        if (!exchangeId)
        {
            return;
        }

        if (isLoadingExchange)
        {
            return;
        }

        setIsLoadingExchange(true);

        try
        {
            const exchange = await fetchNoAuthExchange(exchangeId) as NoAuthExchangeBasicDto;

            if (exchange.status === ExchangeStatus.INITIATED)
            {
                setExchangeAccepted(false);
            }
            else if (exchange.status === ExchangeStatus.ACCEPTED_STARTED)
            {
                setExchangeAccepted(true);
            }
            else if (exchange.status === ExchangeStatus.RESCINDED)
            {
                navigate('/sign-in');
                return;
            }
            else
            {
                navigate('/sign-in');
            }

            setExchange(exchange);
        }
        catch (error)
        {
            console.error(error);
            navigate('/sign-in');
        }
        finally
        {
            setIsLoadingExchange(false);
        }
    };

    useEffect(() =>
    {
        if (exchangeId)
        {
            fetchExchange();
        }
    }, [exchangeId]);

    const onExchangeAccepted = (exchange: NoAuthExchangeBasicDto) =>
    {
        setExchange(exchange);
        setExchangeAccepted(true)
    }

    const onDocumentUploaded = (uploadedDocument: DocumentDetailedDto) =>
    {
        setExchange((prev) =>
        {
            if (!prev)
            {
                return prev;
            }
            const updatedDocuments = (prev.documents || []).map((document) =>
                document.id === uploadedDocument.id ? {...document, ...uploadedDocument} : document
            );
            return {...prev, documents: updatedDocuments};
        });
    }

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
                            size={"medium"}
                            label={"Preparing your secure document request"}
                        />
                    </div>
                )}

                {!isLoadingExchange && exchange && (
                    <>
                        {exchangeAccepted ? (
                            <NoAuthExchangeWorkspace exchange={exchange} onDocumentUploaded={onDocumentUploaded}/>
                        ) : (
                            <section
                                id={"no-auth-exchange-decision"}
                                className={styles.exchangeDecisionContainer}
                            >
                                <NoAuthExchangeUserDecision
                                    exchange={exchange}
                                    onAccepted={onExchangeAccepted}
                                    onDeclined={() => navigate("/sign-in/")}
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
