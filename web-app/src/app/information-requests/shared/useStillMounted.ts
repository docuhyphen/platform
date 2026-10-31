import {useCallback, useEffect, useRef} from "react";

export const useStillMounted = (): (() => boolean) => {
    const mounted = useRef(false);

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
        };
    }, []);

    return useCallback(() => mounted.current, []);
};
