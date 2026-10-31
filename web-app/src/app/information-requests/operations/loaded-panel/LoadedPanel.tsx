import React from "react";
import {Spinner, Text} from "@fluentui/react-components";
import {LoadedValue} from "../../../../hooks/useLoadedValue.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";

interface LoadedPanelProps<T>
{
    idPrefix: string;
    loaded: LoadedValue<T>;
    loadingLabel: string;
    emptyText?: string;
    isEmpty?: (value: T) => boolean;
    children: (value: T) => React.ReactNode;
}

const LoadedPanel = <T, >({idPrefix, loaded, loadingLabel, emptyText, isEmpty, children}: LoadedPanelProps<T>) =>
{
    if (loaded.error)
    {
        return (
            <InformationRequestLoadError id={`${idPrefix}-error`}
                                         message={loaded.error}
                                         onRetry={loaded.reload}/>
        );
    }
    if (loaded.value === null)
    {
        return (
            <Spinner id={`${idPrefix}-loading`}
                     size={"small"}
                     label={loadingLabel}/>
        );
    }
    if (emptyText && isEmpty?.(loaded.value))
    {
        return <Text id={`${idPrefix}-empty`}>{emptyText}</Text>;
    }
    return <>{children(loaded.value)}</>;
};

export default LoadedPanel;
