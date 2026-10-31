import {createContext, useContext} from "react";

export const RequestReadRefreshContext = createContext(0);
export const useRequestReadRefresh = (): number => useContext(RequestReadRefreshContext);
