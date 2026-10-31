import {useState} from "react";
import {informationRequestRefusalMessage} from "./informationRequestRefusal.ts";

export const useDialogCommand = (failure?: string | null, notice?: string | null) => {
    const [busy, setBusy] = useState(false);
    const [failed, setFailed] = useState(false);
    const [unexpected, setUnexpected] = useState<string | null>(null);
    const reset = () => {
        setFailed(false);
        setUnexpected(null);
    };
    const run = async (command: () => unknown | Promise<unknown>, close: () => void) => {
        setBusy(true);
        reset();
        try {
            if (await command() === null) setFailed(true);
            else close();
        } catch (caught: unknown) {
            setUnexpected(informationRequestRefusalMessage(caught, "The change could not be made. Try again."));
            setFailed(true);
        } finally {
            setBusy(false);
        }
    };
    return {
        busy,
        run,
        reset,
        error: failed ? unexpected ?? failure ?? notice ?? "The change could not be made. Try again." : null
    };
};
