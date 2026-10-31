import {useCallback, useEffect, useRef, useState} from "react";
import {getExchangeInformationRequests} from "../../../services/informationRequestAuthoringService.ts";
import {ExchangeStatus, InformationRequestExchangeListingDto} from "../../models/models.tsx";
import {informationRequestRefusalCode, informationRequestRefusalMessage} from "../shared/informationRequestRefusal.ts";

const ACCESS_REFUSED = "INFORMATION_REQUEST_FORBIDDEN";

export interface ExchangeInformationRequestsState
{
    listing: InformationRequestExchangeListingDto | null;
    loading: boolean;
    error: string | null;
    visible: boolean;
    reload: () => Promise<void>;
}

interface KeyedListing
{
    exchangeId: string;
    listing: InformationRequestExchangeListingDto;
}

export const useExchangeInformationRequests = (exchangeId?: string, exchangeStatus?: ExchangeStatus): ExchangeInformationRequestsState => {
    const [keyed, setKeyed] = useState<KeyedListing | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [accessRefused, setAccessRefused] = useState(false);
    const latestLoad = useRef(0);

    const reload = useCallback(async () =>
    {
        const load = ++latestLoad.current;
        if (!exchangeId)
        {
            setKeyed(null);
            setError(null);
            return;
        }
        setLoading(true);
        setError(null);
        setAccessRefused(false);
        try
        {
            const loaded = await getExchangeInformationRequests(exchangeId);
            if (load === latestLoad.current) setKeyed({exchangeId, listing: loaded});
        }
        catch (caught: unknown)
        {
            if (load !== latestLoad.current) return;
            setKeyed(null);
            setAccessRefused(informationRequestRefusalCode(caught) === ACCESS_REFUSED);
            setError(informationRequestRefusalMessage(caught, "The Information Requests could not be loaded."));
        }
        finally
        {
            if (load === latestLoad.current) setLoading(false);
        }
    }, [exchangeId]);

    useEffect(() =>
    {
        void reload();
    }, [reload, exchangeStatus]);

    const listing = keyed && keyed.exchangeId === exchangeId ? keyed.listing : null;

    return {
        listing,
        loading,
        error,
        visible: listing
            ? listing.requests.length > 0 || listing.canCreate || Boolean(listing.creationUnavailableReason)
            : Boolean(error && !accessRefused),
        reload,
    };
};
