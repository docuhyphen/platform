import {useEffect, useRef} from "react";

export const useReloadWhenChanged = (key: string, reload: () => Promise<void>) => {
    const seen = useRef(key);

    useEffect(() => {
        if (seen.current === key) return;
        seen.current = key;
        void reload();
    }, [key, reload]);
};
