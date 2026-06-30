import React, {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {NoAuthExchangeBasicDto, ExchangeStatus} from "../models/models.tsx";
import NoAuthExchangeHeader from "./components/header/NoAuthExchangeHeader.tsx";
import {useNoAuthExchangeStyles} from "./NoAuthExchangeStyles.tsx";
import NoAuthExchangeUserDecision from "./components/exchange-use-decision/NoAuthExchangeUserDecision.tsx";
import NoAuthExchangeDocumentList from "./components/document-list/NoAuthExchangeDocumentList.tsx";
import {Spinner, Text} from "@fluentui/react-components";
import {fetchNoAuthExchange} from "../../services/exchangeApi.ts";

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

    return (
        <section className={styles.container}>
            <NoAuthExchangeHeader/>

            {isLoadingExchange && (
                <div className={styles.exchangeLoadingContainer}>
                    <Spinner size={"small"} label={"Loading..."}/>
                </div>
            )}

            {!isLoadingExchange && exchange && (
                <>
                    {exchangeAccepted ? (
                        <section className={styles.exchangeContainer}>
                            <div className={styles.name}>
                                {(exchange.initiatorLastName && exchange.initiatorFirstName) &&
                                    <Text>
                                        Requested by {exchange.initiatorFirstName} {exchange.initiatorLastName}
                                    </Text>
                                }
                                <Text size={600}>{exchange.name}</Text>
                            </div>
                            <NoAuthExchangeDocumentList
                                exchange={exchange}/>
                        </section>
                    ) : (
                        <section className={styles.exchangeDecisionContainer}>
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
    );
};

export default NoAuthExchange;
