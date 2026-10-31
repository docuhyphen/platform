import type {ReactNode} from "react";
import {RequestReadRefreshContext} from "./RequestReadRefreshContext.ts";

export const RequestReadRefreshProvider = ({generation, children}: { generation: number; children: ReactNode }) => (
    <RequestReadRefreshContext.Provider value={generation}>{children}</RequestReadRefreshContext.Provider>
);
