import {useEffect, useRef, useState} from "react";
import {recreateRejectedExchangeObservable} from "../../observable/exchangeObservables.ts";
import {ExchangeInitiationFormState, ExchangeInitiationTransitionDirection} from "../exchangeInitiationTypes.ts";
import {resetExchangeInitiationFields} from "../exchangeInitiationFormDefaults.ts";
import {applyRecreatedExchangeDraft} from "../exchangeInitiationDraft.ts";

interface ExchangeInitiationDialogOptions
{
    form: ExchangeInitiationFormState;
    canUseBlueprints: boolean;
    clearAppliedBlueprint: () => void;
    clearCreatedSummary: () => void;
    loadCatalog: () => void;
}

const useExchangeInitiationDialog = (options: ExchangeInitiationDialogOptions) =>
{
    const {form, canUseBlueprints, clearAppliedBlueprint, clearCreatedSummary, loadCatalog} = options;
    const [isDialogOpen, setIsDialogOpen] = useState(false);
    const [saveBlueprintDialogOpen, setSaveBlueprintDialogOpen] = useState(false);
    const [contentTransitionDirection, setContentTransitionDirection] =
        useState<ExchangeInitiationTransitionDirection>(null);
    const formRef = useRef(form);

    useEffect(() =>
    {
        formRef.current = form;
    });

    const openBlueprintPicker = () =>
    {
        if (!canUseBlueprints) return;
        setContentTransitionDirection("forward");
        form.setChoosingBlueprint(true);
    };

    const closeBlueprintPicker = () =>
    {
        setContentTransitionDirection("back");
        form.setChoosingBlueprint(false);
    };

    const completeBlueprintChoice = () =>
    {
        setContentTransitionDirection("forward");
        form.setChoosingBlueprint(false);
        form.setSelectedTab('recipients-tab');
    };

    const openSaveBlueprintPanel = () =>
    {
        setContentTransitionDirection("forward");
        setSaveBlueprintDialogOpen(true);
    };

    const closeSaveBlueprintPanel = () =>
    {
        setContentTransitionDirection("back");
        setSaveBlueprintDialogOpen(false);
    };

    const resetInitiationForm = () =>
    {
        clearAppliedBlueprint();
        resetExchangeInitiationFields(form);
        clearCreatedSummary();
    };

    const closeDialog = () =>
    {
        resetInitiationForm();
        setIsDialogOpen(false);
    };

    const onDialogOpenChange = (_: unknown, data: { open: boolean }) =>
    {
        if (data.open)
        {
            form.setExchangeInitiatedSuccessfully(false);
            clearCreatedSummary();
            form.setMessageGroupMessages([]);
            loadCatalog();
        }
        else
        {
            resetInitiationForm();
        }
        setIsDialogOpen(data.open);
    };

    useEffect(() =>
    {
        const subscription = recreateRejectedExchangeObservable.subscribe(draft =>
        {
            clearAppliedBlueprint();
            applyRecreatedExchangeDraft(formRef.current, draft);
            setIsDialogOpen(true);
        });

        return () => subscription.unsubscribe();
    }, [clearAppliedBlueprint]);

    return {
        isDialogOpen,
        setIsDialogOpen,
        saveBlueprintDialogOpen,
        contentTransitionDirection,
        openBlueprintPicker,
        closeBlueprintPicker,
        completeBlueprintChoice,
        openSaveBlueprintPanel,
        closeSaveBlueprintPanel,
        resetInitiationForm,
        closeDialog,
        onDialogOpenChange,
    };
};

export type ExchangeInitiationDialogState = ReturnType<typeof useExchangeInitiationDialog>;

export default useExchangeInitiationDialog;
