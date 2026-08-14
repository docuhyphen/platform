import {tabIds} from "../../settingsTabs.ts";

const SETUP_PASSWORD_PARAM = "setupPassword";
const STEP_UP_PARAM = "stepUp";
const SETTINGS_TAB_PARAM = "tab";

export const buildSetupPasswordReturnTo = (search: string): string =>
{
    const params = new URLSearchParams(search);
    params.set(SETTINGS_TAB_PARAM, tabIds.linkedAccounts);
    params.set(SETUP_PASSWORD_PARAM, "true");
    params.delete(STEP_UP_PARAM);
    return `/settings?${params.toString()}`;
};

export const shouldResumeSetupPassword = (search: string): boolean =>
{
    const params = new URLSearchParams(search);
    return params.get(SETUP_PASSWORD_PARAM) === "true" && params.get(STEP_UP_PARAM) === "success";
};

export const clearSetupPasswordReturnParams = (search: string): string =>
{
    const params = new URLSearchParams(search);
    params.delete(SETUP_PASSWORD_PARAM);
    params.delete(STEP_UP_PARAM);
    const query = params.toString();
    return query ? `/settings?${query}` : "/settings";
};
