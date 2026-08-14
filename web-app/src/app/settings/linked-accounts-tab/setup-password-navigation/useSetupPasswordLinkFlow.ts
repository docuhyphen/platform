import {useEffect, useState} from "react";
import {requestStepUp} from "../../../../services/stepUpBroker.ts";
import {
    buildSetupPasswordReturnTo,
    clearSetupPasswordReturnParams,
    shouldResumeSetupPassword,
} from "./setupPasswordNavigation.ts";

export const useSetupPasswordLinkFlow = () =>
{
    const [passwordDialogOpen, setPasswordDialogOpen] = useState(false);

    useEffect(() =>
    {
        if (!shouldResumeSetupPassword(window.location.search)) return;

        setPasswordDialogOpen(true);
        window.history.replaceState(
            window.history.state,
            "",
            clearSetupPasswordReturnParams(window.location.search),
        );
    }, []);

    const beginPasswordSetup = async () =>
    {
        await requestStepUp({
            action: "add email and password sign-in",
            returnTo: buildSetupPasswordReturnTo(window.location.search),
        });
        setPasswordDialogOpen(true);
    };

    return {
        passwordDialogOpen,
        beginPasswordSetup,
        closePasswordDialog: () => setPasswordDialogOpen(false),
    };
};
