import React, {useEffect} from 'react';

const HEADER_EXPANDED_STORAGE_KEY = 'exchanges.header.isExpanded';

export const useExchangeHeaderExpansion = () =>
{
    const [isExpanded, setIsExpanded] = React.useState(() =>
    {
        if (typeof window === 'undefined') return false;
        const saved = window.localStorage.getItem(HEADER_EXPANDED_STORAGE_KEY);
        return saved === 'true';
    });

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(HEADER_EXPANDED_STORAGE_KEY, String(isExpanded));
    }, [isExpanded]);

    const toggleHeaderDetails = () =>
    {
        setIsExpanded((prev) => !prev);
    };

    const detailsToggleTooltip = isExpanded ? "Collapse details" : "Expand details";

    return {isExpanded, toggleHeaderDetails, detailsToggleTooltip};
};
