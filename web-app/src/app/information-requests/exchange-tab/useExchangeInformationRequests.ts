import {useCallback, useEffect, useRef, useState} from "react";
import {getExchangeInformationRequests} from "../../../services/informationRequestAuthoringService.ts";
import {InformationRequestExchangeListingDto} from "../../models/models.tsx";
import {informationRequestRefusalMessage} from "../shared/informationRequestRefusal.ts";

export interface ExchangeInformationRequestsState
{
    listing: InformationRequestExchangeListingDto | null;
    loading: boolean;
    error: string | null;
    visible: boolean;
    reload: () => Promise<void>;
}

export const useExchangeInformationRequests = (exchangeId?: string): ExchangeInformationRequestsState =>
{
    const [listing, setListing] = useState<InformationRequestExchangeListingDto | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const latestLoad = useRef(0);

    const reload = useCallback(async () =>
    {
        const load = ++latestLoad.current;
        if (!exchangeId)
        {
            setListing(null);
            setError(null);
            return;
        }
        setLoading(true);
        setError(null);
        try
        {
            const loaded = await getExchangeInformationRequests(exchangeId);
            if (load === latestLoad.current) setListing(loaded);
        }
        catch (caught: unknown)
        {
            if (load !== latestLoad.current) return;
            setListing(null);
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
    }, [reload]);

    return {
        listing,
        loading,
        error,
        visible: Boolean(listing && (listing.requests.length > 0 || listing.canCreate)),
        reload,
    };
};
