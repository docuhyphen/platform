import {isInformationRequestForbidden} from "../../shared/informationRequestRefusal.ts";

export const reportingForbidden = <T, >(read: Promise<T>, onForbidden?: () => void): Promise<T> =>
    read.catch((refusal: unknown) => {
        if (isInformationRequestForbidden(refusal)) onForbidden?.();
        throw refusal;
    });
