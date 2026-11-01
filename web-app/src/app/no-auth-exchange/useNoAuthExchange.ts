import {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {DocumentDetailedDto, ExchangeStatus, NoAuthExchangeBasicDto} from "../models/models.tsx";
import {fetchNoAuthExchange, storeNoAuthExchangeAccessToken} from "../../services/exchangeApi.ts";

const useNoAuthExchange = () =>
{
    const navigate = useNavigate();

    const [isLoadingExchange, setIsLoadingExchange] = useState(false);
    const [exchangeId, setExchangeId] = useState<string | null>(null);
    const [exchangeAccepted, setExchangeAccepted] = useState(false);
    const [exchange, setExchange] = useState<NoAuthExchangeBasicDto>(null);

    useEffect(() =>
    {
        const queryParams = new URLSearchParams(window.location.search);
        const exchangeIdParam = queryParams.get('s');
        const accessTokenParam = queryParams.get('t');

        if (!exchangeIdParam)
        {
            navigate('/sign-in');
        }
        else
        {
            if (accessTokenParam)
            {
                storeNoAuthExchangeAccessToken(exchangeIdParam, accessTokenParam);
                queryParams.delete('t');
                const remainingQuery = queryParams.toString();
                window.history.replaceState(
                    null,
                    '',
                    `${window.location.pathname}${remainingQuery ? `?${remainingQuery}` : ''}`,
                );
            }
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

    const onExchangeDeclined = () => navigate("/sign-in/");

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

    return {
        isLoadingExchange,
        exchange,
        exchangeAccepted,
        onExchangeAccepted,
        onExchangeDeclined,
        onDocumentUploaded,
    };
};

export default useNoAuthExchange;
