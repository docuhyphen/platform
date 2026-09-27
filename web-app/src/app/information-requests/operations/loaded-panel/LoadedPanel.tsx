import React from "react";
import {MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {LoadedValue} from "../../../../hooks/useLoadedValue.ts";

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
            <MessageBar id={`${idPrefix}-error`}
                        intent={"error"}>
                <MessageBarBody>{loaded.error}</MessageBarBody>
            </MessageBar>
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
