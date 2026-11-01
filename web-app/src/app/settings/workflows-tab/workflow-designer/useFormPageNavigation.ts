import {useCallback, useState} from "react";
import {FormPage, FormPageTransitionDirection} from "./workflowDesignerDefaults.ts";

export const useFormPageNavigation = () =>
{
    const [formPage, setFormPage] = useState<FormPage>("overview");
    const [transitionDirection, setTransitionDirection] = useState<FormPageTransitionDirection>(null);

    const navigate = (nextPage: FormPage) =>
    {
        setTransitionDirection(nextPage === "overview" ? "back" : "forward");
        setFormPage(nextPage);
    };

    const reset = useCallback(() =>
    {
        setFormPage("overview");
        setTransitionDirection(null);
    }, []);

    return {formPage, transitionDirection, navigate, reset};
};
